package com.example.telecom

import android.content.Context
import android.util.Log
import com.example.data.CallRepository
import com.example.data.CustomerRepository
import com.example.domain.PhoneNormalizer
import com.example.domain.model.CustomerContext
import com.example.integration.TaloolaPosCallerGatewayImpl
import com.example.integration.TaloolaPosConnection
import com.example.mock.MockTaloolaPosCallerGateway
import com.example.security.SecureStorageRepository
import com.example.storage.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallManager private constructor(context: Context) {

    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val secureStorage = SecureStorageRepository(applicationContext)
    val appDatabase = AppDatabase.getDatabase(applicationContext)
    val taloolaConnection = TaloolaPosConnection(applicationContext, secureStorage)
    private val realGateway = TaloolaPosCallerGatewayImpl(taloolaConnection, secureStorage)
    private val mockGateway = MockTaloolaPosCallerGateway()

    val customerRepository = CustomerRepository(realGateway, mockGateway, secureStorage, appDatabase)
    val callRepository = CallRepository(realGateway, mockGateway, secureStorage, appDatabase)

    private val _activeIncomingCall = MutableStateFlow<CustomerContext?>(null)
    val activeIncomingCall: StateFlow<CustomerContext?> = _activeIncomingCall.asStateFlow()

    private val _isLookupLoading = MutableStateFlow(false)
    val isLookupLoading: StateFlow<Boolean> = _isLookupLoading.asStateFlow()

    init {
        // Automatically sync queued calls as soon as connection is READY
        scope.launch {
            taloolaConnection.connectionState.collect { state ->
                if (state == com.example.domain.model.ConnectionState.READY) {
                    try {
                        val count = callRepository.syncPendingCalls()
                        if (count > 0) {
                            Log.i(TAG, "Automatically synced $count offline calls upon reconnect")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-sync pending calls failed: ${e.message}")
                    }
                }
            }
        }
    }

    fun onIncomingCallReceived(rawPhone: String) {
        val normalized = PhoneNormalizer.normalize(rawPhone)
        Log.i(TAG, "Incoming call received: $rawPhone -> $normalized")

        scope.launch {
            _isLookupLoading.value = true
            try {
                // Record call locally immediately
                callRepository.recordCall(rawPhone)

                // Forward immediately to TaloolaPos via SignalR OnIncomingCall
                taloolaConnection.sendIncomingCall(
                    callerNumber = rawPhone,
                    callerName = null,
                    lineId = "SIM 1",
                    timestamp = System.currentTimeMillis()
                )

                // Asynchronously lookup customer info in TaloolaPos / Local Cache
                val customerContext = customerRepository.getCustomerContext(rawPhone)
                _activeIncomingCall.value = customerContext

                // Show high priority notification if in background
                NotificationHelper.showIncomingCallNotification(applicationContext, customerContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming call: ${e.message}")
            } finally {
                _isLookupLoading.value = false
            }
        }
    }

    fun dismissCurrentCall() {
        _activeIncomingCall.value = null
        NotificationHelper.cancelNotification(applicationContext)
    }

    companion object {
        private const val TAG = "CallManager"

        @Volatile
        private var INSTANCE: CallManager? = null

        fun getInstance(context: Context): CallManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CallManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
