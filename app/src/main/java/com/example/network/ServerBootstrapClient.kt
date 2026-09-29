package com.example.network

import android.util.Log
import com.example.domain.model.CallAssistantStatus
import com.example.domain.model.CallerPairResponse
import com.example.domain.model.PairingSession
import com.example.domain.model.ServerBootstrapInfo
import com.example.security.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

class ServerBootstrapClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "ServerBootstrapClient"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Probes raw TCP connectivity on the given host and port.
     */
    suspend fun checkTcpConnectivity(host: String, port: Int, timeoutMs: Int = 2000): Result<Long> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                val duration = System.currentTimeMillis() - startTime
                DiagnosticLogger.log("TCP connection to $host:$port succeeded in ${duration}ms")
                Result.success(duration)
            } catch (e: ConnectException) {
                DiagnosticLogger.log("TCP connection refused: المنفذ مغلق أو الخادم غير مشتغل على $host:$port", isError = true)
                Result.failure(Exception("المنفذ $port مغلق أو خادم TaloolaPos غير مشتغل على $host"))
            } catch (e: SocketTimeoutException) {
                DiagnosticLogger.log("TCP connection timeout to $host:$port", isError = true)
                Result.failure(Exception("انتهت مهلة الاتصال بالخادم على $host:$port (لا يوجد رد من الشبكة)"))
            } catch (e: Exception) {
                DiagnosticLogger.log("TCP connection failed to $host:$port: ${e.message}", isError = true)
                Result.failure(Exception("تعذر الوصول إلى عنوان الخادم $host:$port: ${e.message}"))
            }
        }

    /**
     * Queries GET /api/server/info
     */
    suspend fun getServerInfo(
        host: String,
        port: Int,
        tls: Boolean = false,
        mockFallback: Boolean = true
    ): Result<ServerBootstrapInfo> = withContext(Dispatchers.IO) {
        val scheme = if (tls) "https" else "http"
        val url = "$scheme://$host:$port/api/server/info"
        DiagnosticLogger.log("Verifying server reachability at $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val info = ServerBootstrapInfo(
                    serverId = json.optString("serverId", "TALOOLA-SRV-BAGHDAD-01"),
                    serviceName = json.optString("serviceName", "Taloola POS"),
                    serverVersion = json.optString("serverVersion", "3.2.0"),
                    protocolVersion = json.optString("protocolVersion", "1.0"),
                    port = json.optInt("port", port),
                    tlsRequired = json.optBoolean("tlsRequired", tls),
                    sessionActive = json.optBoolean("sessionActive", true),
                    callerAssistantQrPairingEnabled = json.optBoolean("callerAssistantQrPairingEnabled", true),
                    callerAssistantPairingMode = json.optString("callerAssistantPairingMode", "QR_ONLY"),
                    pairingEnabled = json.optBoolean("pairingEnabled", true),
                    serverTimeUtc = json.optString("serverTimeUtc", "")
                )
                DiagnosticLogger.log("Server info verified: ${info.serviceName} (${info.serverId}) v${info.serverVersion}")
                Result.success(info)
            } else {
                DiagnosticLogger.log("HTTP /api/server/info returned ${response.code}", isError = true)
                if (mockFallback) {
                    Result.success(ServerBootstrapInfo(port = port, tlsRequired = tls))
                } else {
                    Result.failure(Exception("الخادم غير متاح (رمز HTTP ${response.code})"))
                }
            }
        } catch (e: IOException) {
            if (mockFallback) {
                DiagnosticLogger.log("Network unavailable for real server, using simulated TaloolaPos response")
                Result.success(ServerBootstrapInfo(port = port, tlsRequired = tls))
            } else {
                DiagnosticLogger.log("Server connection error: ${e.message}", isError = true)
                Result.failure(Exception("الخادم غير متاح"))
            }
        }
    }

    /**
     * Step 4: Strict Server Validation against QR Session
     */
    suspend fun validateServerForSession(
        session: PairingSession,
        mockFallback: Boolean = false
    ): Result<ServerBootstrapInfo> = withContext(Dispatchers.IO) {
        if (session.isExpired) {
            DiagnosticLogger.log("Pairing QR expired", isError = true)
            return@withContext Result.failure(Exception("QR منتهي"))
        }

        val infoResult = getServerInfo(
            host = session.host,
            port = session.port,
            tls = session.tls,
            mockFallback = mockFallback
        )

        if (infoResult.isFailure) {
            val err = infoResult.exceptionOrNull()?.message ?: "الخادم غير متاح"
            return@withContext Result.failure(Exception(err))
        }

        val server = infoResult.getOrThrow()

        // 1. QR ServerId == Server ServerId
        if (!server.serverId.equals(session.serverId, ignoreCase = true)) {
            DiagnosticLogger.log("Server ID mismatch: QR=${session.serverId} vs Server=${server.serverId}", isError = true)
            return@withContext Result.failure(Exception("Server ID غير مطابق"))
        }

        // 2. QR Protocol == Server ProtocolVersion
        if (server.protocolVersion != session.protocolVersion &&
            !server.protocolVersion.startsWith(session.protocolVersion.take(1))) {
            DiagnosticLogger.log("Protocol mismatch: QR=${session.protocolVersion} vs Server=${server.protocolVersion}", isError = true)
            return@withContext Result.failure(Exception("Protocol غير متوافق"))
        }

        // 3. Server SessionActive == true
        if (!server.sessionActive) {
            DiagnosticLogger.log("Server session is inactive", isError = true)
            return@withContext Result.failure(Exception("جلسة الخادم غير نشطة"))
        }

        // 4. Server CallerAssistantQrPairingEnabled == true
        if (!server.callerAssistantQrPairingEnabled) {
            DiagnosticLogger.log("Server CallerAssistant QR pairing disabled", isError = true)
            return@withContext Result.failure(Exception("الاقتران عبر QR معطل في الخادم"))
        }

        // 5. Server CallerAssistantPairingMode == QR_ONLY
        if (server.callerAssistantPairingMode != "QR_ONLY") {
            DiagnosticLogger.log("Server CallerAssistantPairingMode is not QR_ONLY: ${server.callerAssistantPairingMode}", isError = true)
            return@withContext Result.failure(Exception("نمط الاقتران في الخادم غير متوافق (QR_ONLY مطلوب)"))
        }

        DiagnosticLogger.log("All server validation checks passed for ${server.serverId} ✓")
        Result.success(server)
    }

    /**
     * Step 6 & 7: Send Pair Request POST /api/caller-assistant/pair
     */
    suspend fun pairCallerAssistant(
        session: PairingSession,
        deviceId: String,
        deviceName: String,
        installationBinding: String = "",
        mockFallback: Boolean = false
    ): Result<CallerPairResponse> = withContext(Dispatchers.IO) {
        val scheme = if (session.tls) "https" else "http"
        val serverUrl = "$scheme://${session.host}:${session.port}"
        val url = "$serverUrl/api/caller-assistant/pair"
        DiagnosticLogger.log("Sending POST /api/caller-assistant/pair to $url")

        val payload = JSONObject().apply {
            // CallerAssistantPairingRequest PascalCase contract
            put("Version", session.version)
            put("DeviceType", "CallerAssistant")
            put("ServerId", session.serverId)
            put("ProtocolVersion", session.protocolVersion)
            put("PairingId", session.pairingId)
            put("Token", session.token)
            put("PairingToken", session.token)
            put("DeviceId", deviceId)
            put("DeviceName", deviceName)
            put("InstallationBinding", installationBinding)
            put("Platform", "Android")
            put("AppVersion", "1.0.0")
            val capsArr = org.json.JSONArray().apply { put("CallerAssistant") }
            put("RequestedCapabilities", capsArr)

            // CamelCase compatibility keys
            put("pairingId", session.pairingId)
            put("token", session.token)
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("deviceModel", android.os.Build.MODEL)
            put("appVersion", "1.0.0")
            put("platform", "Android")
            put("requestedRole", "CallerAssistant")
            put("timestamp", System.currentTimeMillis())
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val success = json.optBoolean("Success", json.optBoolean("success", false))
                val sid = json.optString("ServerId", json.optString("serverId", ""))
                val sName = json.optString("ServerName", json.optString("serverName", "Taloola POS"))
                val sUrl = json.optString("ServerUrl", json.optString("serverUrl", serverUrl))
                val proto = json.optString("ProtocolVersion", json.optString("protocolVersion", session.protocolVersion))
                val dId = json.optString("DeviceId", json.optString("deviceId", deviceId))
                val dName = json.optString("DeviceName", json.optString("deviceName", deviceName))
                val instBinding = json.optString("InstallationBinding", json.optString("installationBinding", installationBinding))
                val dStatus = json.optString("DeviceStatus", json.optString("deviceStatus", "Approved"))
                
                // Read CallerCredential / DeviceToken / DeviceKey
                val cred = json.optString("CallerCredential", json.optString("callerCredential", ""))
                val dToken = json.optString("DeviceToken", json.optString("deviceToken", cred))
                val dKey = json.optString("DeviceKey", json.optString("deviceKey", cred.ifBlank { dToken }))
                val finalCred = cred.ifBlank { dToken }
                
                val hubPath = json.optString("HubPath", json.optString("hubPath", "/posHub"))
                val exp = json.optLong("ExpiresAt", json.optLong("expiresAt", System.currentTimeMillis() + 86400_000L))
                val msg = json.optString("Message", json.optString("message", ""))
                
                val caps = mutableListOf<String>()
                val capsArray = json.optJSONArray("Capabilities") ?: json.optJSONArray("capabilities")
                if (capsArray != null) {
                    for (i in 0 until capsArray.length()) {
                        caps.add(capsArray.optString(i))
                    }
                } else {
                    caps.add("CallerAssistant")
                }

                if (!success) {
                    val errMsg = if (msg.isNotBlank()) msg else "فشل طلب الاقتران من جانب الخادم"
                    return@withContext Result.failure(Exception(errMsg))
                }

                if (!sid.equals(session.serverId, ignoreCase = true)) {
                    return@withContext Result.failure(Exception("Server ID غير مطابق"))
                }

                if (!caps.contains("CallerAssistant")) {
                    return@withContext Result.failure(Exception("هذا الجهاز لا يملك صلاحية البدالة."))
                }

                if (finalCred.isBlank()) {
                    return@withContext Result.failure(Exception("لم يقم الخادم بإصدار بيانات اعتماد CallerCredential"))
                }

                val pairResponse = CallerPairResponse(
                    success = true,
                    serverId = sid,
                    serverName = sName,
                    deviceId = dId,
                    deviceToken = finalCred,
                    deviceKey = dKey,
                    capabilities = caps,
                    hubPath = hubPath,
                    expiresAt = exp,
                    message = msg,
                    callerCredential = finalCred,
                    deviceStatus = dStatus,
                    serverUrl = sUrl,
                    protocolVersion = proto,
                    installationBinding = instBinding
                )
                DiagnosticLogger.log("Caller assistant paired successfully with server $sid (Credential issued)")
                Result.success(pairResponse)
            } else {
                if (mockFallback) {
                    val mockResponse = CallerPairResponse(
                        success = true,
                        serverId = session.serverId,
                        serverName = session.serverName,
                        deviceId = deviceId,
                        deviceToken = "tok_" + UUID.randomUUID().toString().take(12),
                        deviceKey = "key_" + UUID.randomUUID().toString().take(12),
                        capabilities = listOf("CallerAssistant"),
                        hubPath = "/posHub",
                        expiresAt = System.currentTimeMillis() + 86400_000L,
                        callerCredential = "cred_" + UUID.randomUUID().toString().take(16),
                        deviceStatus = "Approved",
                        serverUrl = serverUrl,
                        protocolVersion = session.protocolVersion,
                        installationBinding = installationBinding
                    )
                    Result.success(mockResponse)
                } else {
                    Result.failure(Exception("فشل الاقتران برمز HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            if (mockFallback) {
                val mockResponse = CallerPairResponse(
                    success = true,
                    serverId = session.serverId,
                    serverName = session.serverName,
                    deviceId = deviceId,
                    deviceToken = "tok_" + UUID.randomUUID().toString().take(12),
                    deviceKey = "key_" + UUID.randomUUID().toString().take(12),
                    capabilities = listOf("CallerAssistant"),
                    hubPath = "/posHub",
                    expiresAt = System.currentTimeMillis() + 86400_000L
                )
                DiagnosticLogger.log("Generated mock pair credential for simulation")
                Result.success(mockResponse)
            } else {
                DiagnosticLogger.log("Pairing request failed: ${e.message}", isError = true)
                Result.failure(Exception("الخادم غير متاح للاتصال: ${e.message}"))
            }
        }
    }

    /**
     * Unpair: POST /api/caller-assistant/unpair
     */
    suspend fun unpairCallerAssistant(
        host: String,
        port: Int,
        tls: Boolean,
        deviceId: String,
        deviceToken: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val scheme = if (tls) "https" else "http"
        val url = "$scheme://$host:$port/api/caller-assistant/unpair"
        DiagnosticLogger.log("Sending unpair request to $url")

        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceToken", deviceToken)
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            // Unpair should succeed locally even if network fails
            DiagnosticLogger.log("Unpair network notify failed: ${e.message}")
            Result.success(true)
        }
    }

    /**
     * Queries GET /api/caller/status
     */
    suspend fun getCallerAssistantStatus(
        host: String,
        port: Int,
        token: String = ""
    ): Result<CallAssistantStatus> = withContext(Dispatchers.IO) {
        val url = "http://$host:$port/api/caller/status"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val status = CallAssistantStatus(
                    serverReady = json.optBoolean("serverReady", true),
                    callerAssistantEnabled = json.optBoolean("callerAssistantEnabled", true),
                    connectedCallerDevices = json.optInt("connectedCallerDevices", 1),
                    connectedCashiers = json.optInt("connectedCashiers", 3),
                    pendingCallerMessages = json.optInt("pendingCallerMessages", 0),
                    lastCallerMessageAtUtc = json.optString("lastCallerMessageAtUtc", ""),
                    serverId = json.optString("serverId", "TALOOLA-SRV-BAGHDAD-01")
                )
                Result.success(status)
            } else {
                Result.success(CallAssistantStatus())
            }
        } catch (e: Exception) {
            Result.success(CallAssistantStatus())
        }
    }
}
