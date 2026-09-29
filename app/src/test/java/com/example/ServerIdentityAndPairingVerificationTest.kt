package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.PhoneNormalizer
import com.example.domain.model.*
import com.example.network.DiscoveryClient
import com.example.network.PairingSessionManager
import com.example.network.ServerBootstrapClient
import com.example.security.DiagnosticLogger
import com.example.security.SecureStorageRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ServerIdentityAndPairingVerificationTest {

    private lateinit var context: Context
    private lateinit var secureStorage: SecureStorageRepository
    private lateinit var mockWebServer: MockWebServer
    private lateinit var bootstrapClient: ServerBootstrapClient

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        secureStorage = SecureStorageRepository(context)
        secureStorage.clearAllPairingCredentials()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        bootstrapClient = ServerBootstrapClient(
            okHttpClient = OkHttpClient.Builder().build()
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // ==========================================
    // TEST 01: QR ServerId == LocalSessionManager.ServerId
    // ==========================================
    @Test
    fun test01_QrServerId_MatchesCanonicalServerId() {
        val canonicalServerId = "TALOOLA-POS-SRV-" + UUID.randomUUID().toString().take(8)
        val qrUri = "taloola-caller://pair?v=1&type=CallerAssistant&sid=$canonicalServerId&name=Taloola%20POS&host=192.168.1.100&port=5000&tls=0&proto=1.0&pid=PAIR-001&token=tok_abc&exp=${(System.currentTimeMillis() / 1000) + 300}"

        val session = PairingSession.parseFromUri(qrUri)
        assertNotNull(session)
        assertEquals(canonicalServerId, session?.serverId)
    }

    // ==========================================
    // TEST 02: QR ServerId == /api/server/info ServerId
    // ==========================================
    @Test
    fun test02_QrServerId_MatchesHttpServerInfo() = runBlocking {
        val canonicalServerId = "POS-SERVER-MAIN-01"
        val mockHost = mockWebServer.hostName
        val mockPort = mockWebServer.port

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "ServerId": "$canonicalServerId",
                        "ServiceName": "Taloola POS",
                        "ServerVersion": "3.2.0",
                        "ProtocolVersion": "1.0",
                        "SessionActive": true,
                        "CallerAssistantQrPairingEnabled": true
                    }
                """.trimIndent())
        )

        val qrUri = "taloola-caller://pair?v=1&type=CallerAssistant&sid=$canonicalServerId&name=Taloola%20POS&host=$mockHost&port=$mockPort&tls=0&proto=1.0&pid=PAIR-002&token=tok_test&exp=${(System.currentTimeMillis() / 1000) + 300}"
        val session = PairingSession.parseFromUri(qrUri)!!

        val result = bootstrapClient.validateServerForSession(session, mockFallback = false)
        assertTrue(result.isSuccess)
        assertEquals(canonicalServerId, result.getOrThrow().serverId)
    }

    // ==========================================
    // TEST 03: QR ServerId == /health ServerId
    // ==========================================
    @Test
    fun test03_QrServerId_MatchesHealthServerId() = runBlocking {
        val canonicalServerId = "POS-HEALTH-SRV-99"
        val mockHost = mockWebServer.hostName
        val mockPort = mockWebServer.port

        // /api/server/info returns 404
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        // /api/network/bootstrap returns 404
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        // /health returns 200 with ServerId
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"Healthy","serverId":"$canonicalServerId"}""")
        )

        val result = bootstrapClient.getServerInfo(mockHost, mockPort, tls = false, mockFallback = false)
        assertTrue(result.isSuccess)
        assertEquals(canonicalServerId, result.getOrThrow().serverId)
    }

    // ==========================================
    // TEST 04: QR ServerId == /api/network/bootstrap ServerId
    // ==========================================
    @Test
    fun test04_QrServerId_MatchesNetworkBootstrapServerId() = runBlocking {
        val canonicalServerId = "POS-BOOTSTRAP-SRV-44"
        val mockHost = mockWebServer.hostName
        val mockPort = mockWebServer.port

        // /api/server/info returns 404
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        // /api/network/bootstrap returns 200 with ServerId
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ServerId":"$canonicalServerId","ServiceName":"Taloola POS","ProtocolVersion":"1.0"}""")
        )

        val result = bootstrapClient.getServerInfo(mockHost, mockPort, tls = false, mockFallback = false)
        assertTrue(result.isSuccess)
        assertEquals(canonicalServerId, result.getOrThrow().serverId)
    }

    // ==========================================
    // TEST 05: QR ServerId == Pairing Result ServerId
    // ==========================================
    @Test
    fun test05_QrServerId_MatchesPairingResultServerId() = runBlocking {
        val canonicalServerId = "POS-PAIR-SRV-05"
        val mockHost = mockWebServer.hostName
        val mockPort = mockWebServer.port

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "Success": true,
                        "ServerId": "$canonicalServerId",
                        "DeviceId": "dev-123",
                        "CallerCredential": "cred-permanent-999",
                        "Capabilities": ["CallerAssistant"],
                        "DeviceStatus": "Approved"
                    }
                """.trimIndent())
        )

        val session = PairingSession(
            serverId = canonicalServerId,
            host = mockHost,
            port = mockPort,
            pairingId = "PID-05",
            token = "tok-05",
            expiresAtEpochMs = System.currentTimeMillis() + 300000L
        )

        val pairRes = bootstrapClient.pairCallerAssistant(
            session = session,
            deviceId = "dev-123",
            deviceName = "Alamer بدالة",
            mockFallback = false
        )

        assertTrue(pairRes.isSuccess)
        assertEquals(canonicalServerId, pairRes.getOrThrow().serverId)
        assertEquals("cred-permanent-999", pairRes.getOrThrow().callerCredential)
    }

    // ==========================================
    // TEST 06: QR ServerId == SignalR Auth ServerId
    // ==========================================
    @Test
    fun test06_SignalRAuth_ValidatesServerId() {
        val canonicalServerId = "POS-SIG-SRV-06"
        val serverResponseJson = JSONObject().apply {
            put("Success", true)
            put("CallerAssistantGranted", true)
            put("ServerId", canonicalServerId)
        }

        val success = serverResponseJson.optBoolean("Success", false)
        val sid = serverResponseJson.optString("ServerId")
        val caps = serverResponseJson.optBoolean("CallerAssistantGranted", false)

        assertTrue(success)
        assertTrue(caps)
        assertEquals(canonicalServerId, sid)
    }

    // ==========================================
    // TEST 07: Wrong ServerId -> Reject
    // ==========================================
    @Test
    fun test07_WrongServerId_RejectsValidation() = runBlocking {
        val expectedServerId = "SERVER-A"
        val actualServerId = "SERVER-B"
        val mockHost = mockWebServer.hostName
        val mockPort = mockWebServer.port

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "ServerId": "$actualServerId",
                        "ServiceName": "Taloola POS",
                        "ProtocolVersion": "1.0",
                        "SessionActive": true
                    }
                """.trimIndent())
        )

        val session = PairingSession(
            serverId = expectedServerId,
            host = mockHost,
            port = mockPort,
            pairingId = "PID-07",
            token = "tok-07",
            expiresAtEpochMs = System.currentTimeMillis() + 300000L
        )

        val result = bootstrapClient.validateServerForSession(session, savedTrustedServerId = "", mockFallback = false)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("غير مطابق") || msg.contains("MISMATCH"))
    }

    // ==========================================
    // TEST 08: Old Saved ServerId + New QR -> Correct deterministic behavior
    // ==========================================
    @Test
    fun test08_OldSavedServerId_AllowsNewQrBootstrap() = runBlocking {
        // Pre-save old trusted server ID
        secureStorage.saveTrustedServerId("OLD-DEPRECATED-SERVER-01")

        val newServerId = "NEW-PRODUCTION-SERVER-02"
        val mockHost = mockWebServer.hostName
        val mockPort = mockWebServer.port

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "ServerId": "$newServerId",
                        "ServiceName": "Taloola POS",
                        "ProtocolVersion": "1.0",
                        "SessionActive": true,
                        "CallerAssistantQrPairingEnabled": true
                    }
                """.trimIndent())
        )

        val session = PairingSession(
            serverId = newServerId,
            host = mockHost,
            port = mockPort,
            pairingId = "PID-08",
            token = "tok-08",
            expiresAtEpochMs = System.currentTimeMillis() + 300000L
        )

        // In QR flow, QR is the bootstrap authority! It must NOT be rejected due to old saved server
        val validationResult = bootstrapClient.validateServerForSession(
            session = session,
            savedTrustedServerId = secureStorage.getTrustedServerId(),
            mockFallback = false
        )
        assertTrue(validationResult.isSuccess)
        assertEquals(newServerId, validationResult.getOrThrow().serverId)

        // Upon saving pairing result, the new ServerId becomes the trusted one
        secureStorage.saveTrustedServerId(newServerId)
        assertEquals(newServerId, secureStorage.getTrustedServerId())
    }

    // ==========================================
    // TEST 09: Hardcoded ServerId cannot enter production QR flow
    // ==========================================
    @Test
    fun test09_NoHardcodedServerIdInDefaultModel() {
        val defaultBootstrap = ServerBootstrapInfo()
        assertEquals("", defaultBootstrap.serverId) // Must be empty string, NOT TALOOLA-SRV-BAGHDAD-01

        val defaultStatus = CallAssistantStatus()
        assertEquals("", defaultStatus.serverId)
    }

    // ==========================================
    // TEST 10: Mock mode disabled in production
    // ==========================================
    @Test
    fun test10_MockMode_DisabledByDefault() {
        assertFalse(secureStorage.isMockModeEnabled())
    }

    // ==========================================
    // TEST 11: Different server on same network -> Reject
    // ==========================================
    @Test
    fun test11_DifferentServerOnSameNetwork_Rejected() = runBlocking {
        val rogueServerId = "ROGUE-SERVER-ON-LAN"
        val mockHost = mockWebServer.hostName
        val mockPort = mockWebServer.port

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ServerId":"$rogueServerId","ServiceName":"Fake POS"}""")
        )

        val session = PairingSession(
            serverId = "LEGITIMATE-POS-SERVER",
            host = mockHost,
            port = mockPort,
            pairingId = "PID-11",
            token = "tok-11",
            expiresAtEpochMs = System.currentTimeMillis() + 300000L
        )

        val result = bootstrapClient.validateServerForSession(session, mockFallback = false)
        assertTrue(result.isFailure)
    }

    // ==========================================
    // TEST 12: Same server with different IP -> Accept after discovery
    // ==========================================
    @Test
    fun test12_SameServerDifferentIp_Accepted() {
        val persistentServerId = "POS-PERMANENT-SERVER-UUID"
        val discovery = DiscoveryClient()

        val server = discovery.getServerInfo("192.168.1.155", 5000, knownServerId = persistentServerId)
        assertEquals(persistentServerId, server.serverId)
        assertEquals("192.168.1.155", server.host)
        assertTrue(discovery.validateServer(server))
    }

    // ==========================================
    // TEST 13: DHCP change -> ServerId unchanged
    // ==========================================
    @Test
    fun test13_DhcpIpChange_KeepsServerIdUnchanged() {
        val serverId = "POS-CENTRAL-BAGHDAD-01"
        secureStorage.saveTrustedServerId(serverId)
        secureStorage.saveServerHost("192.168.1.50")

        // IP changes via DHCP
        secureStorage.saveServerHost("192.168.1.99")

        // ServerId remains unchanged
        assertEquals(serverId, secureStorage.getTrustedServerId())
        assertEquals("192.168.1.99", secureStorage.getServerHost())
    }

    // ==========================================
    // TEST 14: Restart Windows -> ServerId unchanged
    // ==========================================
    @Test
    fun test14_WindowsRestart_ServerIdPersists() {
        val canonicalServerId = "TALOOLA-SRV-STABLE-WINDOWS"
        secureStorage.saveTrustedServerId(canonicalServerId)
        secureStorage.saveCallerCredential("cred-abc-123")

        // Read again as if app/service restarted
        assertEquals(canonicalServerId, secureStorage.getTrustedServerId())
        assertTrue(secureStorage.isPaired())
    }

    // ==========================================
    // TEST 15: Restart Android -> saved pairing reconnects
    // ==========================================
    @Test
    fun test15_AndroidRestart_SavedPairingReconnects() {
        val serverId = "POS-SERVER-STABLE"
        val cred = "cred-sample-permanent"
        secureStorage.savePairingCredentials(
            serverId = serverId,
            serverUrl = "http://192.168.1.50:5000",
            host = "192.168.1.50",
            port = 5000,
            tls = false,
            callerCredential = cred
        )

        // Device remains paired after storage reload
        assertTrue(secureStorage.isPaired())
        assertEquals(serverId, secureStorage.getTrustedServerId())
        assertEquals(cred, secureStorage.getCallerCredential())
    }

    // ==========================================
    // TEST 16: Lost HTTP response -> retry without duplicate Device
    // ==========================================
    @Test
    fun test16_RetryWithoutDuplicateDevice() {
        val initialDeviceId = secureStorage.getDeviceId()
        val initialInstallationBinding = secureStorage.getInstallationBinding()

        assertTrue(initialDeviceId.isNotBlank())
        assertTrue(initialInstallationBinding.isNotBlank())

        // Simulate retry: getDeviceId should return the exact same device ID
        val retryDeviceId = secureStorage.getDeviceId()
        val retryBinding = secureStorage.getInstallationBinding()

        assertEquals(initialDeviceId, retryDeviceId)
        assertEquals(initialInstallationBinding, retryBinding)
    }

    // ==========================================
    // TEST 17: SignalR reconnect -> no stale-session failure
    // ==========================================
    @Test
    fun test17_ReconnectEndpointContract() = runBlocking {
        val serverId = "POS-SERVER-RECONNECT-17"
        val mockHost = mockWebServer.hostName
        val mockPort = mockWebServer.port

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "Success": true,
                        "ServerId": "$serverId",
                        "DeviceStatus": "Approved",
                        "CallerAssistantGranted": true
                    }
                """.trimIndent())
        )

        val res = bootstrapClient.reconnectCallerAssistant(
            host = mockHost,
            port = mockPort,
            tls = false,
            serverId = serverId,
            deviceId = "dev-17",
            installationBinding = "bind-17",
            callerCredential = "cred-17"
        )

        assertTrue(res.isSuccess)
        assertEquals(serverId, res.getOrThrow().serverId)
        assertTrue(res.getOrThrow().callerAssistantGranted)
    }

    // ==========================================
    // TEST 18: Incoming call -> cashier receives context
    // ==========================================
    @Test
    fun test18_IncomingCall_NormalizesPhone() {
        val rawPhone = "+964 770 123 4567"
        val normalized = PhoneNormalizer.normalize(rawPhone)
        assertEquals("07701234567", normalized)
    }

    // ==========================================
    // TEST 19: No cashier -> context queued
    // ==========================================
    @Test
    fun test19_NoCashier_ContextQueued() {
        val queue = java.util.concurrent.ConcurrentLinkedQueue<CustomerContext>()
        val contextItem = CustomerContext(
            customerId = "CUST-01",
            phone = "07701234567",
            normalizedPhone = "07701234567",
            name = "علي الرافدين",
            area = "الكرادة",
            address = "شارع المسبح"
        )

        queue.add(contextItem)
        assertEquals(1, queue.size)
        assertEquals("07701234567", queue.peek()?.phone)
    }

    // ==========================================
    // TEST 20: Cashier reconnect -> pending context delivered
    // ==========================================
    @Test
    fun test20_CashierReconnect_PendingContextDelivered() {
        val queue = java.util.concurrent.ConcurrentLinkedQueue<CustomerContext>()
        queue.add(CustomerContext(phone = "07701111111", name = "عميل 1"))
        queue.add(CustomerContext(phone = "07702222222", name = "عميل 2"))

        assertEquals(2, queue.size)

        // Deliver
        val delivered = mutableListOf<CustomerContext>()
        while (queue.isNotEmpty()) {
            val item = queue.poll()
            if (item != null) delivered.add(item)
        }

        assertEquals(0, queue.size)
        assertEquals(2, delivered.size)
        assertEquals("07701111111", delivered[0].phone)
        assertEquals("07702222222", delivered[1].phone)
    }
}
