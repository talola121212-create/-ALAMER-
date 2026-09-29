package com.example.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.CallSummary
import com.example.domain.model.CustomerContext
import com.example.domain.model.OrderSummary

@Entity(tableName = "customer_cache")
data class CustomerCacheEntity(
    @PrimaryKey val normalizedPhone: String,
    val customerId: String,
    val phone: String,
    val name: String,
    val status: String,
    val orderCount: Int,
    val area: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String,
    // Last call cached info
    val lastCallId: String?,
    val lastCallStartedAt: String?,
    val lastCallDuration: Int?,
    // Last order cached info
    val lastOrderNumber: String?,
    val lastOrderType: String?,
    val lastOrderTotal: String?,
    val lastOrderStatus: String?,
    val lastOrderCreatedAt: String?,
    val cachedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun toDomain(): CustomerContext {
        val lastCall = if (!lastCallId.isNullOrBlank()) {
            CallSummary(
                callId = lastCallId,
                direction = "INCOMING",
                startedAtUtc = lastCallStartedAt ?: "",
                durationSeconds = lastCallDuration ?: 0
            )
        } else null

        val lastOrder = if (!lastOrderNumber.isNullOrBlank()) {
            OrderSummary(
                orderNumber = lastOrderNumber,
                orderType = lastOrderType ?: "",
                total = lastOrderTotal ?: "",
                status = lastOrderStatus ?: "",
                createdAtUtc = lastOrderCreatedAt ?: ""
            )
        } else null

        return CustomerContext(
            customerId = customerId,
            phone = phone,
            normalizedPhone = normalizedPhone,
            name = name,
            status = status,
            lastCall = lastCall,
            lastOrder = lastOrder,
            orderCount = orderCount,
            area = area,
            address = address,
            latitude = latitude,
            longitude = longitude,
            notes = notes,
            retrievedAtUtc = ""
        )
    }

    companion object {
        fun fromDomain(domain: CustomerContext): CustomerCacheEntity {
            return CustomerCacheEntity(
                normalizedPhone = domain.normalizedPhone,
                customerId = domain.customerId,
                phone = domain.phone,
                name = domain.name,
                status = domain.status,
                orderCount = domain.orderCount,
                area = domain.area,
                address = domain.address,
                latitude = domain.latitude,
                longitude = domain.longitude,
                notes = domain.notes,
                lastCallId = domain.lastCall?.callId,
                lastCallStartedAt = domain.lastCall?.startedAtUtc,
                lastCallDuration = domain.lastCall?.durationSeconds,
                lastOrderNumber = domain.lastOrder?.orderNumber,
                lastOrderType = domain.lastOrder?.orderType,
                lastOrderTotal = domain.lastOrder?.total,
                lastOrderStatus = domain.lastOrder?.status,
                lastOrderCreatedAt = domain.lastOrder?.createdAtUtc,
                cachedAtEpochMs = System.currentTimeMillis()
            )
        }
    }
}

@Entity(tableName = "call_events")
data class CallEventEntity(
    @PrimaryKey val callId: String,
    val phone: String,
    val normalizedPhone: String,
    val direction: String,
    val startedAtUtc: String,
    val endedAtUtc: String?,
    val durationSeconds: Int,
    val customerId: String,
    val deviceId: String,
    val operatorId: String,
    val createdAtUtc: String,
    val isSynced: Boolean = false
)
