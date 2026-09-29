package com.example.network

import android.util.Log
import com.example.domain.model.*
import com.example.security.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class ServerBootstrapClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "ServerBootstrapClient"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Probes raw TCP connectivity on the given host and port.
     */
    suspend fun checkTcpConnectivity(host: String, port: Int, timeoutMs: Int = 2500): Result<Long> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                val duration = System.currentTimeMillis() - startTime
                DiagnosticLogger.log("TCP probe to $host:$port succeeded in ${duration}ms")
                Result.success(duration)
            } catch (e: ConnectException) {
                DiagnosticLogger.log("TCP connection refused on $host:$port (خادم TaloolaPos غير مشغل أو المنفذ مغلق)", isError = true)
                Result.failure(Exception("الخادم غير متاح: المنفذ $port مغلق أو برنامج TaloolaPos غير مشغل على $host"))
            } catch (e: SocketTimeoutException) {
                DiagnosticLogger.log("TCP timeout to $host:$port (انتهت مهلة الانتظار)", isError = true)
                Result.failure(Exception("الخادم غير متاح: انتهت مهلة الاتصال بالخادم $host:$port"))
            } catch (e: Exception) {
                DiagnosticLogger.log("TCP connection failed to $host:$port: ${e.message}", isError = true)
                Result.failure(Exception("تعذر الوصول إلى الخادم $host:$port: ${e.message}"))
            }
        }

    /**
     * Queries GET /api/server/info (with secondary fallback to /api/network/bootstrap or /health)
     * Extracting Canonical ServerId from LocalSessionManager.
     */
    suspend fun getServerInfo(
        host: String,
        port: Int,
        tls: Boolean = false,
        mockFallback: Boolean = false
    ): Result<ServerBootstrapInfo> = withContext(Dispatchers.IO) {
        val scheme = if (tls) "https" else "http"
        val primaryUrl = "$scheme://$host:$port/api/server/info"
        DiagnosticLogger.log("Querying server identity from $primaryUrl")

        val endpointsToTry = listOf(
            primaryUrl,
            "$scheme://$host:$port/api/network/bootstrap",
            "$scheme://$host:$port/health"
        )

        var lastError: Exception? = null

        for (url in endpointsToTry) {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }

                    // Extract ServerId case-insensitively
                    val sid = json.optString("ServerId", json.optString("serverId", "")).ifBlank {
                        json.optString("sid", json.optString("id", ""))
                    }

                    if (sid.isNotBlank()) {
                        val sName = json.optString("ServiceName", json.optString("serviceName", "Taloola POS"))
                        val sVer = json.optString("ServerVersion", json.optString("serverVersion", "3.2.0"))
                        val protoVer = json.optString("ProtocolVersion", json.optString("protocolVersion", "1.0"))
                        val svrPort = json.optInt("Port", json.optInt("port", port))
                        val tlsReq = json.optBoolean("TlsRequired", json.optBoolean("tlsRequired", tls))
                        val active = json.optBoolean("SessionActive", json.optBoolean("sessionActive", true))
                        val qrEnabled = json.optBoolean(
                            "CallerAssistantQrPairingEnabled",
                            json.optBoolean("callerAssistantQrPairingEnabled", json.optBoolean("PairingEnabled", json.optBoolean("pairingEnabled", true)))
                        )
                        val pairMode = json.optString("CallerAssistantPairingMode", json.optString("callerAssistantPairingMode", "QR_ONLY"))
                        val pairingEnabled = json.optBoolean("PairingEnabled", json.optBoolean("pairingEnabled", true))
                        val timeUtc = json.optString("ServerTimeUtc", json.optString("serverTimeUtc", ""))

                        val info = ServerBootstrapInfo(
                            serverId = sid,
                            serviceName = sName,
                            serverVersion = sVer,
                            protocolVersion = protoVer,
                            port = svrPort,
                            tlsRequired = tlsReq,
                            sessionActive = active,
                            callerAssistantQrPairingEnabled = qrEnabled,
                            callerAssistantPairingMode = pairMode,
                            pairingEnabled = pairingEnabled,
                            serverTimeUtc = timeUtc
                        )
                        DiagnosticLogger.log("Canonical server identity resolved: ${info.serviceName} (ServerId=$sid)")
                        return@withContext Result.success(info)
                    }
                }
            } catch (e: IOException) {
                lastError = e
            }
        }

        if (mockFallback) {
            DiagnosticLogger.log("Notice: Using mock bootstrap info (mock mode enabled)")
            return@withContext Result.success(ServerBootstrapInfo(serverId = "MOCK-POS-SRV", port = port, tlsRequired = tls))
        }

        DiagnosticLogger.log("Server verification failed at $host:$port: ${lastError?.message ?: "خادم غير متاح"}", isError = true)
        Result.failure(Exception("الخادم غير متاح: تعذر استرجاع هوية الخادم من $host:$port"))
    }

    /**
     * Strict Server Validation against QR Session:
     * - Stage B: QR Validation
     * - Stage C: GET /api/server/info
     * - Stage D: ServerId Comparison
     */
    suspend fun validateServerForSession(
        session: PairingSession,
        savedTrustedServerId: String = "",
        mockFallback: Boolean = false
    ): Result<ServerBootstrapInfo> = withContext(Dispatchers.IO) {
        // Stage B: QR expiration
        if (session.isExpired) {
            val diag = "Stage: Stage B (QR validation)\nQR Expired at: ${session.expiresAtEpochMs}\nCurrent Time: ${System.currentTimeMillis()}"
            DiagnosticLogger.log(diag, isError = true)
            return@withContext Result.failure(Exception("رمز QR منتهي الصلاحية، يرجى إنشاء رمز جديد من TaloolaPos"))
        }

        // Stage C: Query HTTP /api/server/info
        val infoResult = getServerInfo(
            host = session.host,
            port = session.port,
            tls = session.tls,
            mockFallback = mockFallback
        )

        if (infoResult.isFailure) {
            val diag = "Stage: Stage C (GET /api/server/info)\nEndpoint: ${session.serverUrl}/api/server/info\nError: ${infoResult.exceptionOrNull()?.message}"
            DiagnosticLogger.log(diag, isError = true)
            return@withContext Result.failure(Exception("الخادم غير متاح: تعذر الوصول إلى TaloolaPos عبر ${session.host}:${session.port}"))
        }

        val server = infoResult.getOrThrow()

        // Stage D: ServerId Comparison
        // Must match QR ServerId == HTTP ServerId (Case-Insensitive)
        if (!server.serverId.equals(session.serverId, ignoreCase = true)) {
            val diag = """
                [SERVER_ID_MISMATCH]
                Stage: Stage D (ServerId comparison)
                QR ServerId: '${session.serverId}'
                HTTP ServerId: '${server.serverId}'
                Saved ServerId: '${savedTrustedServerId.ifBlank { "NONE" }}'
                Endpoint: '${session.serverUrl}/api/server/info'
                PairingId: '${session.pairingId}'
                Protocol: '${session.protocolVersion}'
            """.trimIndent()
            DiagnosticLogger.log(diag, isError = true)

            return@withContext Result.failure(
                Exception("معرّف الخادم غير مطابق:\nالرمز يطلب: '${session.serverId}'\nالخادم الفعلي: '${server.serverId}'\nالمرحلة: Stage D (ServerId comparison)")
            )
        }

        // Protocol version check (tolerant of 1.x and 3.x)
        val serverMajor = server.protocolVersion.substringBefore(".").ifBlank { "1" }
        val sessionMajor = session.protocolVersion.substringBefore(".").ifBlank { "1" }
        if (serverMajor != sessionMajor && server.protocolVersion != session.protocolVersion) {
            val diag = "Stage: Protocol check\nQR Protocol: ${session.protocolVersion}\nServer Protocol: ${server.protocolVersion}"
            DiagnosticLogger.log(diag, isError = true)
            return@withContext Result.failure(Exception("إصدار البروتوكول غير متوافق: الخادم يعمل بإصدار ${server.protocolVersion} والتطبيق يطلب ${session.protocolVersion}"))
        }

        // Session Active check
        if (!server.sessionActive) {
            DiagnosticLogger.log("Server session is inactive on ${server.serverId}", isError = true)
            return@withContext Result.failure(Exception("جلسة الخادم غير نشطة حالياً في TaloolaPos"))
        }

        // CallerAssistant QR Pairing Enabled
        if (!server.callerAssistantQrPairingEnabled && !server.pairingEnabled) {
            DiagnosticLogger.log("CallerAssistant pairing disabled on server ${server.serverId}", isError = true)
            return@withContext Result.failure(Exception("الاقتران كبدالة معطل حالياً في إعدادات TaloolaPos"))
        }

        DiagnosticLogger.log("Server validation PASSED: ServerId=${server.serverId} Protocol=${server.protocolVersion} ✓")
        Result.success(server)
    }

    /**
     * Step E: Send Pair Request POST /api/caller-assistant/pair
     * ServerId sent in request is strictly QR.ServerId
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
        DiagnosticLogger.log("Stage E: Sending POST /api/caller-assistant/pair to $url (ServerId=${session.serverId})")

        val payload = JSONObject().apply {
            // PascalCase C# contract
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
            val capsArr = JSONArray().apply { put("CallerAssistant") }
            put("RequestedCapabilities", capsArr)

            // camelCase mirrors for alternate serializers
            put("version", session.version)
            put("deviceType", "CallerAssistant")
            put("serverId", session.serverId)
            put("protocolVersion", session.protocolVersion)
            put("pairingId", session.pairingId)
            put("token", session.token)
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("installationBinding", installationBinding)
            put("platform", "Android")
            put("appVersion", "1.0.0")
            put("requestedCapabilities", capsArr)
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
                val sid = json.optString("ServerId", json.optString("serverId", "")).ifBlank {
                    json.optString("sid", "")
                }
                val sName = json.optString("ServerName", json.optString("serverName", "Taloola POS"))
                val sUrl = json.optString("ServerUrl", json.optString("serverUrl", serverUrl))
                val proto = json.optString("ProtocolVersion", json.optString("protocolVersion", session.protocolVersion))
                val respDeviceId = json.optString("DeviceId", json.optString("deviceId", deviceId))
                val instBinding = json.optString("InstallationBinding", json.optString("installationBinding", installationBinding))
                val dStatus = json.optString("DeviceStatus", json.optString("deviceStatus", "Approved"))

                // CallerCredential or DeviceToken
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
                    val errMsg = if (msg.isNotBlank()) msg else "رفض خادم TaloolaPos طلب الاقتران"
                    DiagnosticLogger.log("Stage E failed: Server rejected pairing ($errMsg)", isError = true)
                    return@withContext Result.failure(Exception(errMsg))
                }

                // Verify ServerId matches QR ServerId
                if (sid.isNotBlank() && !sid.equals(session.serverId, ignoreCase = true)) {
                    val diag = "Stage: Stage E (Pair response validation)\nExpected ServerId: ${session.serverId}\nReceived ServerId: $sid"
                    DiagnosticLogger.log(diag, isError = true)
                    return@withContext Result.failure(Exception("معرّف الخادم في استجابة الاقتران ($sid) غير مطابق للرمز (${session.serverId})"))
                }

                // Verify DeviceId matches
                if (respDeviceId.isNotBlank() && respDeviceId != deviceId) {
                    DiagnosticLogger.log("Device ID mismatch: Sent $deviceId, Received $respDeviceId", isError = true)
                    return@withContext Result.failure(Exception("معرّف الجهاز غير مطابق في استجابة الخادم"))
                }

                // Verify Capabilities contains CallerAssistant
                if (!caps.contains("CallerAssistant")) {
                    DiagnosticLogger.log("Server did not grant CallerAssistant capability", isError = true)
                    return@withContext Result.failure(Exception("لم يمنح الخادم هذا الجهاز صلاحية البدالة (CallerAssistant)."))
                }

                // Verify CallerCredential is present
                if (finalCred.isBlank()) {
                    DiagnosticLogger.log("Missing CallerCredential in server response", isError = true)
                    return@withContext Result.failure(Exception("لم يقم الخادم بإصدار بيانات الاعتماد (CallerCredential)"))
                }

                val pairResponse = CallerPairResponse(
                    success = true,
                    serverId = sid.ifBlank { session.serverId },
                    serverName = sName,
                    deviceId = respDeviceId,
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
                DiagnosticLogger.log("Stage E PASSED: Pairing approved with ServerId=${pairResponse.serverId} ✓")
                Result.success(pairResponse)
            } else {
                DiagnosticLogger.log("Stage E HTTP Error ${response.code}", isError = true)
                Result.failure(Exception("فشل الاقتران برمز HTTP ${response.code} من خادم TaloolaPos"))
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("Stage E network failure: ${e.message}", isError = true)
            Result.failure(Exception("تعذر الاتصال بنقطة الاقتران: ${e.message}"))
        }
    }

    /**
     * Reconnect: POST /api/caller-assistant/reconnect
     * Validates existing permanent CallerCredential without scanning QR.
     */
    suspend fun reconnectCallerAssistant(
        host: String,
        port: Int,
        tls: Boolean,
        serverId: String,
        deviceId: String,
        installationBinding: String,
        callerCredential: String,
        protocolVersion: String = "1.0"
    ): Result<CallerAssistantReconnectResult> = withContext(Dispatchers.IO) {
        val scheme = if (tls) "https" else "http"
        val url = "$scheme://$host:$port/api/caller-assistant/reconnect"
        DiagnosticLogger.log("Sending POST /api/caller-assistant/reconnect to $url (ServerId=$serverId)")

        val payload = JSONObject().apply {
            put("ServerId", serverId)
            put("DeviceId", deviceId)
            put("InstallationBinding", installationBinding)
            put("CallerCredential", callerCredential)
            put("DeviceToken", callerCredential)
            put("ProtocolVersion", protocolVersion)
            put("DeviceType", "CallerAssistant")
            put("Platform", "Android")

            // camelCase mirrors
            put("serverId", serverId)
            put("deviceId", deviceId)
            put("installationBinding", installationBinding)
            put("callerCredential", callerCredential)
            put("deviceToken", callerCredential)
            put("protocolVersion", protocolVersion)
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
                val success = json.optBoolean("Success", json.optBoolean("success", true))
                val sid = json.optString("ServerId", json.optString("serverId", serverId))
                val dStatus = json.optString("DeviceStatus", json.optString("deviceStatus", "Approved"))
                val capsGranted = json.optBoolean("CallerAssistantGranted", json.optBoolean("callerAssistantGranted", true))
                val msg = json.optString("Message", json.optString("message", ""))

                if (!success) {
                    return@withContext Result.failure(Exception(if (msg.isNotBlank()) msg else "رفض الخادم إعادة الاتصال"))
                }

                val result = CallerAssistantReconnectResult(
                    success = true,
                    serverId = sid,
                    deviceStatus = dStatus,
                    callerAssistantGranted = capsGranted,
                    message = msg
                )
                DiagnosticLogger.log("Reconnect validated by server $sid ✓")
                Result.success(result)
            } else {
                Result.failure(Exception("فشل إعادة الاتصال برمز HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            DiagnosticLogger.log("Reconnect request network failure: ${e.message}", isError = true)
            Result.failure(e)
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
            put("DeviceId", deviceId)
            put("DeviceToken", deviceToken)
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
            DiagnosticLogger.log("Unpair network notify notice: ${e.message}")
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
                val sid = json.optString("ServerId", json.optString("serverId", ""))
                val status = CallAssistantStatus(
                    serverReady = json.optBoolean("serverReady", true),
                    callerAssistantEnabled = json.optBoolean("callerAssistantEnabled", true),
                    connectedCallerDevices = json.optInt("connectedCallerDevices", 1),
                    connectedCashiers = json.optInt("connectedCashiers", 3),
                    pendingCallerMessages = json.optInt("pendingCallerMessages", 0),
                    lastCallerMessageAtUtc = json.optString("lastCallerMessageAtUtc", ""),
                    serverId = sid
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
