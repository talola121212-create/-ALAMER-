package com.example.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CallEvent
import com.example.domain.model.CustomerContext
import com.example.telecom.CallManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallerViewModel(application: Application) : AndroidViewModel(application) {

    private val callManager = CallManager.getInstance(application)
    private val customerRepository = callManager.customerRepository
    private val callRepository = callManager.callRepository

    val activeCall: StateFlow<CustomerContext?> = callManager.activeIncomingCall
    val isLookupLoading: StateFlow<Boolean> = callManager.isLookupLoading
    val connectionState = callManager.taloolaConnection.connectionState

    val recentCalls: StateFlow<List<CallEvent>> = callRepository.allCalls
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingCount: StateFlow<Int> = callRepository.pendingCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun simulateCall(phone: String) {
        callManager.onIncomingCallReceived(phone)
    }

    fun dismissCall() {
        callManager.dismissCurrentCall()
    }

    fun sendToCashier() {
        val current = activeCall.value ?: return
        viewModelScope.launch {
            val result = customerRepository.sendContextToCashier(current)
            if (result.isSuccess) {
                _userMessage.emit("تم إرسال بيانات العميل إلى الكاشير بنجاح ✓")
            } else {
                _userMessage.emit("فشل الإرسال إلى الكاشير: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}")
            }
        }
    }

    fun createCustomer(name: String, area: String, address: String) {
        val current = activeCall.value ?: return
        viewModelScope.launch {
            val result = customerRepository.createCustomer(current.phone, name, area, address)
            if (result.isSuccess) {
                _userMessage.emit("تم إنشاء العميل وحفظه في TaloolaPos بنجاح ✓")
                // Refresh customer context
                callManager.onIncomingCallReceived(current.phone)
            } else {
                _userMessage.emit("تعذر إنشاء العميل: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun syncPendingEvents() {
        viewModelScope.launch {
            val synced = callRepository.syncPendingCalls()
            if (synced > 0) {
                _userMessage.emit("تمت مزامنة $synced من المكالمات المعلقة بنجاح ✓")
            } else {
                _userMessage.emit("لا توجد مكالمات معلقة بحاجة للمزامنة")
            }
        }
    }
}
