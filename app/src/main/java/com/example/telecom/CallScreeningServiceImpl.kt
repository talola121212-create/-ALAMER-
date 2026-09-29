package com.example.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallScreeningServiceImpl : CallScreeningService() {

    companion object {
        private const val TAG = "CallScreeningService"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // Step 1: Immediately construct the Telecom response allowing the call.
        // NEVER wait for network, SignalR, HTTP, or DB before this step!
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        // Respond to Android Telecom immediately
        respondToCall(callDetails, response)
        Log.d(TAG, "Responded to Android Telecom immediately: Allowed call")

        // Step 2: Extract caller phone number asynchronously
        val handle = callDetails.handle
        val rawNumber = handle?.schemeSpecificPart ?: ""

        if (rawNumber.isNotBlank()) {
            // Hand off to CallManager on background coroutine
            CallManager.getInstance(applicationContext).onIncomingCallReceived(rawNumber)
        }
    }
}
