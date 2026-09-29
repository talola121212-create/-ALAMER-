package com.example.domain.model

import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallSummary(
    val callId: String = "",
    val direction: String = "INCOMING",
    val startedAtUtc: String = "",
    val endedAtUtc: String = "",
    val durationSeconds: Int = 0
)

data class OrderSummary(
    val orderNumber: String = "",
    val orderType: String = "",
    val total: String = "",
    val status: String = "",
    val createdAtUtc: String = ""
)

data class CustomerContext(
    val customerId: String = "",
    val phone: String = "",
    val normalizedPhone: String = "",
    val name: String = "",
    val status: String = "عميل جديد", // "عميل معروف" or "عميل جديد"
    val lastCall: CallSummary? = null,
    val lastOrder: OrderSummary? = null,
    val orderCount: Int = 0,
    val area: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = "",
    val retrievedAtUtc: String = ""
) {
    val isKnownCustomer: Boolean
        get() = status == "عميل معروف" || name.isNotBlank() && name != "غير متوفر"

    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0
}

data class CallEvent(
    val callId: String,
    val phone: String,
    val normalizedPhone: String,
    val direction: String = "INCOMING",
    val startedAtUtc: String,
    val endedAtUtc: String? = null,
    val durationSeconds: Int = 0,
    val customerId: String = "",
    val deviceId: String = "",
    val operatorId: String = "",
    val createdAtUtc: String,
    val isSynced: Boolean = false
)

data class ServerInfo(
    val serverId: String,
    val host: String,
    val port: Int = 5000,
    val serviceName: String = "Taloola POS",
    val protocolVersion: String = "1.0",
    val sessionId: String = "",
    val sessionActive: Boolean = true,
    val serverVersion: String = "3.2.0",
    val masterDataVersion: String = "1"
)

data class ServerBootstrapInfo(
    val serverId: String = "TALOOLA-SRV-BAGHDAD-01",
    val serviceName: String = "Taloola POS",
    val serverVersion: String = "3.2.0",
    val protocolVersion: String = "1.0",
    val port: Int = 5000,
    val tlsRequired: Boolean = false,
    val sessionActive: Boolean = true,
    val callerAssistantQrPairingEnabled: Boolean = true,
    val callerAssistantPairingMode: String = "QR_ONLY",
    val pairingEnabled: Boolean = true,
    val serverTimeUtc: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
)

data class CallerPairResponse(
    val success: Boolean,
    val serverId: String,
    val serverName: String = "Taloola POS",
    val deviceId: String,
    val deviceToken: String,
    val deviceKey: String,
    val capabilities: List<String> = listOf("CallerAssistant"),
    val hubPath: String = "/posHub",
    val expiresAt: Long = 0L,
    val message: String = ""
)

enum class ConnectionState(val arabicLabel: String) {
    DISCONNECTED("غير مقترن"),
    DISCOVERING("جاري البحث عن الخادم"),
    SERVER_FOUND("تم اكتشاف الخادم"),
    VERIFYING_SERVER("جاري التحقق من الخادم"),
    PAIRING_REQUIRED("الاقتران مطلوب"),
    PAIRING_IN_PROGRESS("جاري التحقق والاقتران..."),
    PAIRING_SUCCESS("تم الاقتران بنجاح"),
    AUTHENTICATING("جاري المصادقة عبر SignalR"),
    CONNECTING("جاري الاتصال..."),
    CONNECTED("متصل مع الخادم"),
    READY("متصل مع TaloolaPos"),
    RECONNECTING("جاري إعادة الاتصال..."),
    SESSION_EXPIRED("جلسة منتهية"),
    ACCESS_DENIED("صلاحية مرفوضة"),
    SERVER_UNAVAILABLE("الخادم غير متاح"),
    PROTOCOL_MISMATCH("عدم تطابق في البروتوكول"),
    PAIRING_EXPIRED("رمز الاقتران منتهي"),
    PAIRING_INVALID("بيانات الاقتران غير صالحة")
}

data class PairingSession(
    val version: Int = 1,
    val deviceType: String = "CallerAssistant",
    val serverId: String,
    val serverName: String = "Taloola POS",
    val host: String,
    val port: Int = 5000,
    val tls: Boolean = false,
    val protocolVersion: String = "1.0",
    val pairingId: String,
    val token: String,
    val expiresAtEpochMs: Long,
    val pairingCode: String = "",
    val isConsumed: Boolean = false
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAtEpochMs

    val remainingSeconds: Long
        get() = maxOf(0L, (expiresAtEpochMs - System.currentTimeMillis()) / 1000)

    val serverUrl: String
        get() = "${if (tls) "https" else "http"}://$host:$port"

    val wsUrl: String
        get() = "${if (tls) "wss" else "ws"}://$host:$port/posHub"

    fun toPairingUri(): String {
        val expVal = if (expiresAtEpochMs > 100_000_000_000L) expiresAtEpochMs / 1000L else expiresAtEpochMs
        return "taloola-caller://pair?v=$version&type=$deviceType&sid=$serverId&name=${java.net.URLEncoder.encode(serverName, "UTF-8")}&host=$host&port=$port&tls=${if (tls) 1 else 0}&proto=$protocolVersion&pid=$pairingId&token=$token&exp=$expVal"
    }

    companion object {
        fun parseFromUri(rawUri: String): PairingSession? {
            return try {
                val clean = rawUri.trim()
                if (!clean.startsWith("taloola-caller://pair", ignoreCase = true)) {
                    return null
                }

                val uri = URI(clean)
                val query = uri.query ?: return null
                val params = query.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    URLDecoder.decode(parts[0], "UTF-8") to if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
                }

                val v = params["v"]?.toIntOrNull() ?: 1
                val type = params["type"] ?: "CallerAssistant"
                val sid = params["sid"] ?: params["serverId"] ?: return null
                val name = params["name"] ?: "Taloola POS"
                val host = params["host"] ?: return null
                val port = params["port"]?.toIntOrNull() ?: 5000
                val tls = params["tls"] == "1" || params["tls"]?.equals("true", ignoreCase = true) == true
                val proto = params["proto"] ?: "1.0"
                val pid = params["pid"] ?: params["pairingId"] ?: return null
                val token = params["token"] ?: return null
                val expRaw = params["exp"]?.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + 300)
                val expMs = if (expRaw < 100_000_000_000L) expRaw * 1000L else expRaw

                PairingSession(
                    version = v,
                    deviceType = type,
                    serverId = sid,
                    serverName = name,
                    host = host,
                    port = port,
                    tls = tls,
                    protocolVersion = proto,
                    pairingId = pid,
                    token = token,
                    expiresAtEpochMs = expMs,
                    pairingCode = pid.takeLast(6)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class HeartbeatStatus(
    val lastHeartbeatUtc: String = "",
    val roundTripTimeMs: Long = 0L,
    val isActive: Boolean = false,
    val activeCallsCount: Int = 0
)

data class CallAssistantStatus(
    val serverReady: Boolean = true,
    val isConnected: Boolean = true,
    val capabilityGranted: Boolean = true,
    val callerAssistantEnabled: Boolean = true,
    val connectedCallerDevices: Int = 1,
    val connectedCashiers: Int = 3,
    val pendingCallerMessages: Int = 0,
    val activeCallsCount: Int = 0,
    val lastCallerMessageAtUtc: String = "",
    val lastHeartbeatUtc: String = "",
    val serverId: String = "TALOOLA-SRV-BAGHDAD-01"
)

enum class TestCheckStatus {
    IDLE, RUNNING, SUCCESS, FAILED
}

data class TestConnectionCheckItem(
    val id: String,
    val title: String,
    val status: TestCheckStatus = TestCheckStatus.IDLE,
    val latencyMs: Long? = null,
    val details: String = ""
)

data class ConnectedDeviceInfo(
    val deviceName: String,
    val deviceType: String,
    val ipAddress: String,
    val serverId: String,
    val capabilities: String,
    val status: String,
    val lastSeen: String
)

data class PendingApprovalDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val ipAddress: String,
    val requestedAt: String
)

data class DiagnosticLogEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date()),
    val message: String,
    val isError: Boolean = false
)
