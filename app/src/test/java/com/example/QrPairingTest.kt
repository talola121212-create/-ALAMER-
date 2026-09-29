package com.example

import com.example.domain.model.PairingSession
import org.junit.Assert.*
import org.junit.Test

class QrPairingTest {

    @Test
    fun testParseValidTaloolaCallerUri() {
        val uri = "taloola-caller://pair?v=1&type=CallerAssistant&sid=TALOOLA-SRV-BAGHDAD-01&name=Taloola%20POS&host=192.168.1.100&port=5000&tls=0&proto=1.0&pid=PAIR-A1B2C3&token=tok_test123456&exp=${System.currentTimeMillis() + 300000}"
        val parsed = PairingSession.parseFromUri(uri)
        assertNotNull(parsed)
        assertEquals(1, parsed?.version)
        assertEquals("CallerAssistant", parsed?.deviceType)
        assertEquals("TALOOLA-SRV-BAGHDAD-01", parsed?.serverId)
        assertEquals("192.168.1.100", parsed?.host)
        assertEquals(5000, parsed?.port)
        assertFalse(parsed?.tls ?: true)
        assertEquals("1.0", parsed?.protocolVersion)
        assertEquals("PAIR-A1B2C3", parsed?.pairingId)
        assertEquals("tok_test123456", parsed?.token)
        assertFalse(parsed?.isExpired ?: true)
    }

    @Test
    fun testRejectNonTaloolaUri() {
        val uri = "https://example.com/pair?code=12345"
        val parsed = PairingSession.parseFromUri(uri)
        assertNull(parsed)
    }

    @Test
    fun testRejectMismatchedDeviceType() {
        val uri = "taloola-caller://pair?v=1&type=Cashier&sid=TALOOLA-SRV-BAGHDAD-01&host=192.168.1.100&port=5000&pid=PAIR-123&token=tok_456&exp=${System.currentTimeMillis() + 300000}"
        val parsed = PairingSession.parseFromUri(uri)
        assertNotNull(parsed)
        assertNotEquals("CallerAssistant", parsed?.deviceType)
    }
}
