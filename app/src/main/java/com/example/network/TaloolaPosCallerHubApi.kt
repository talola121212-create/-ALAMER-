package com.example.network

/**
 * Centralized SignalR Hub API contract for TaloolaPos /posHub
 */
object TaloolaPosCallerHubApi {
    // Hub Endpoint
    const val HUB_ENDPOINT = "/posHub"
    const val DEFAULT_PORT = 5000
    const val DISCOVERY_PORT = 5051
    const val PROTOCOL_VERSION = "1.0"
    const val REQUIRED_CAPABILITY = "CallerAssistant"

    // Server-side Methods (Invoked from Android Caller Assistant)
    const val METHOD_AUTHENTICATE = "AuthenticateCallerAssistant"
    const val METHOD_HEARTBEAT = "CallerHeartbeat"
    const val METHOD_INCOMING_CALL = "OnIncomingCall"
    const val METHOD_GET_CUSTOMER_CONTEXT = "GetCallerCustomerContext"
    const val METHOD_GET_LAST_ORDERS = "GetCallerLastOrders"
    const val METHOD_GET_LOCATION = "GetCallerLocation"
    const val METHOD_RECORD_CALL = "RecordCallerCall"
    const val METHOD_SEND_CONTEXT_TO_CASHIER = "SendCallerContextToCashier"
    const val METHOD_GET_ASSISTANT_STATUS = "GetCallerAssistantStatus"

    // Client-side Methods (Pushed from TaloolaPos Server to Caller Assistant)
    const val CLIENT_ON_CASHIER_ACK = "OnCashierOrderAck"
    const val CLIENT_ON_CUSTOMER_UPDATED = "OnCustomerDataUpdated"
    const val CLIENT_ON_SESSION_REVOKED = "OnSessionRevoked"

    // Error Codes
    const val ERROR_SERVER_UNAVAILABLE = "SERVER_UNAVAILABLE"
    const val ERROR_CAPABILITY_DENIED = "CAPABILITY_DENIED"
    const val ERROR_SESSION_EXPIRED = "SESSION_EXPIRED"
    const val ERROR_PROTOCOL_MISMATCH = "PROTOCOL_MISMATCH"
    const val ERROR_CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND"
    const val ERROR_TIMEOUT = "TIMEOUT"
}
