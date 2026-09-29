package com.example.data

import android.util.Log
import com.example.domain.PhoneNormalizer
import com.example.domain.model.CallEvent
import com.example.domain.model.CustomerContext
import com.example.domain.model.OrderSummary
import com.example.integration.TaloolaPosCallerGateway
import com.example.security.SecureStorageRepository
import com.example.storage.AppDatabase
import com.example.storage.CallEventEntity
import com.example.storage.CustomerCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CustomerRepository(
    private val realGateway: TaloolaPosCallerGateway,
    private val mockGateway: TaloolaPosCallerGateway,
    private val secureStorage: SecureStorageRepository,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "CustomerRepository"
    }

    private val activeGateway: TaloolaPosCallerGateway
        get() = if (secureStorage.isMockModeEnabled()) mockGateway else realGateway

    suspend fun getCustomerContext(rawPhone: String): CustomerContext = withContext(Dispatchers.IO) {
        val norm = PhoneNormalizer.normalize(rawPhone)

        // Try active gateway
        val networkResult = activeGateway.getCallerCustomerContext(rawPhone)
        if (networkResult.isSuccess) {
            val context = networkResult.getOrThrow()
            // Cache successful result in local Room DB for offline fallback
            if (context.isKnownCustomer) {
                try {
                    database.customerCacheDao().insertOrUpdate(CustomerCacheEntity.fromDomain(context))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed caching customer context: ${e.message}")
                }
            }
            context
        } else {
            // Offline fallback: check local Room cache
            val cached = database.customerCacheDao().getCustomerByPhone(norm)
            if (cached != null) {
                Log.i(TAG, "Loaded customer from offline local cache for $norm")
                cached.toDomain()
            } else {
                // Unknown/Offline customer placeholder
                CustomerContext(
                    customerId = "",
                    phone = rawPhone,
                    normalizedPhone = norm,
                    name = "",
                    status = "عميل جديد",
                    lastCall = null,
                    lastOrder = null,
                    orderCount = 0,
                    area = "غير متوفر",
                    address = "لا توجد بيانات سابقة",
                    latitude = null,
                    longitude = null,
                    notes = "وضع عدم الاتصال - لم يتم العثور على بيانات سابقة",
                    retrievedAtUtc = ""
                )
            }
        }
    }

    suspend fun getLastOrders(rawPhone: String): List<OrderSummary> = withContext(Dispatchers.IO) {
        activeGateway.getCallerLastOrders(rawPhone).getOrDefault(emptyList())
    }

    suspend fun sendContextToCashier(context: CustomerContext): Result<Boolean> = withContext(Dispatchers.IO) {
        activeGateway.sendCallerContextToCashier(context)
    }

    suspend fun createCustomer(phone: String, name: String, area: String, address: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val res = activeGateway.createCustomer(phone, name, area, address)
            if (res.isSuccess) {
                // Also update local cache
                val norm = PhoneNormalizer.normalize(phone)
                val newContext = CustomerContext(
                    customerId = "CUST-" + System.currentTimeMillis().toString().takeLast(4),
                    phone = phone,
                    normalizedPhone = norm,
                    name = name,
                    status = "عميل معروف",
                    area = area,
                    address = address
                )
                database.customerCacheDao().insertOrUpdate(CustomerCacheEntity.fromDomain(newContext))
            }
            res
        }
}

class CallRepository(
    private val realGateway: TaloolaPosCallerGateway,
    private val mockGateway: TaloolaPosCallerGateway,
    private val secureStorage: SecureStorageRepository,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "CallRepository"
    }

    private val activeGateway: TaloolaPosCallerGateway
        get() = if (secureStorage.isMockModeEnabled()) mockGateway else realGateway

    val allCalls: Flow<List<CallEvent>> = database.callEventDao().getAllCalls().map { list ->
        list.map { entity ->
            CallEvent(
                callId = entity.callId,
                phone = entity.phone,
                normalizedPhone = entity.normalizedPhone,
                direction = entity.direction,
                startedAtUtc = entity.startedAtUtc,
                endedAtUtc = entity.endedAtUtc,
                durationSeconds = entity.durationSeconds,
                customerId = entity.customerId,
                deviceId = entity.deviceId,
                operatorId = entity.operatorId,
                createdAtUtc = entity.createdAtUtc,
                isSynced = entity.isSynced
            )
        }
    }

    val pendingCount: Flow<Int> = database.callEventDao().getPendingCallsCount()

    suspend fun recordCall(rawPhone: String, customerId: String = ""): CallEvent = withContext(Dispatchers.IO) {
        val norm = PhoneNormalizer.normalize(rawPhone)
        val callId = UUID.randomUUID().toString()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
        val deviceId = secureStorage.getDeviceId()
        val operatorId = secureStorage.getOperatorAccount()

        val event = CallEvent(
            callId = callId,
            phone = rawPhone,
            normalizedPhone = norm,
            direction = "INCOMING",
            startedAtUtc = now,
            endedAtUtc = null,
            durationSeconds = 0,
            customerId = customerId,
            deviceId = deviceId,
            operatorId = operatorId,
            createdAtUtc = now,
            isSynced = false
        )

        // 1. Immediately persist into Room
        database.callEventDao().insertCall(
            CallEventEntity(
                callId = event.callId,
                phone = event.phone,
                normalizedPhone = event.normalizedPhone,
                direction = event.direction,
                startedAtUtc = event.startedAtUtc,
                endedAtUtc = event.endedAtUtc,
                durationSeconds = event.durationSeconds,
                customerId = event.customerId,
                deviceId = event.deviceId,
                operatorId = event.operatorId,
                createdAtUtc = event.createdAtUtc,
                isSynced = false
            )
        )

        // 2. Try recording to server asynchronously
        try {
            val serverResult = activeGateway.recordCallerCall(event)
            if (serverResult.isSuccess && serverResult.getOrThrow()) {
                database.callEventDao().markAsSynced(event.callId)
                Log.d(TAG, "Call ${event.callId} synced to TaloolaPos successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Call recording queued for offline sync: ${e.message}")
        }

        event
    }

    suspend fun syncPendingCalls(): Int = withContext(Dispatchers.IO) {
        val pending = database.callEventDao().getPendingUnsyncedCalls()
        var syncedCount = 0
        for (entity in pending) {
            val event = CallEvent(
                callId = entity.callId,
                phone = entity.phone,
                normalizedPhone = entity.normalizedPhone,
                direction = entity.direction,
                startedAtUtc = entity.startedAtUtc,
                endedAtUtc = entity.endedAtUtc,
                durationSeconds = entity.durationSeconds,
                customerId = entity.customerId,
                deviceId = entity.deviceId,
                operatorId = entity.operatorId,
                createdAtUtc = entity.createdAtUtc
            )
            val res = activeGateway.recordCallerCall(event)
            if (res.isSuccess && res.getOrThrow()) {
                database.callEventDao().markAsSynced(entity.callId)
                syncedCount++
            }
        }
        syncedCount
    }
}
