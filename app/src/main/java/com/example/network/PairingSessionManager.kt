package com.example.network

import com.example.domain.model.PairingSession
import com.example.security.DiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PairingSessionManager {

    private val activeSessions = ConcurrentHashMap<String, PairingSession>()
    private val _currentSession = MutableStateFlow<PairingSession?>(null)
    val currentSession: StateFlow<PairingSession?> = _currentSession.asStateFlow()

    init {
        // No hardcoded serverId pre-seeding
    }

    /**
     * Creates a temporary, one-time pairing session.
     */
    fun createPairingSession(
        serverId: String,
        host: String = "127.0.0.1",
        port: Int = 5000,
        tls: Boolean = false,
        durationSeconds: Long = 300
    ): PairingSession {
        val pairingId = "PAIR-" + UUID.randomUUID().toString().take(6).uppercase()
        val token = "tok_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val expiresAt = System.currentTimeMillis() + (durationSeconds * 1000)

        val session = PairingSession(
            version = 1,
            deviceType = "CallerAssistant",
            serverId = serverId,
            serverName = "Taloola POS",
            host = host,
            port = port,
            tls = tls,
            protocolVersion = "1.0",
            pairingId = pairingId,
            token = token,
            expiresAtEpochMs = expiresAt,
            pairingCode = pairingId.takeLast(6),
            isConsumed = false
        )

        activeSessions[pairingId] = session
        activeSessions[token] = session
        _currentSession.value = session
        DiagnosticLogger.log("Created pairing session $pairingId for server $serverId (valid for ${durationSeconds}s)")
        return session
    }

    /**
     * Validates that the pairing session exists, is not consumed, has not expired,
     * and strictly requests CallerAssistant capability.
     */
    fun validatePairingSession(rawInput: String): Result<PairingSession> {
        val resolved = if (rawInput.startsWith("taloola-caller://pair", ignoreCase = true)) {
            PairingSession.parseFromUri(rawInput)
        } else {
            activeSessions[rawInput]
        }

        if (resolved == null) {
            DiagnosticLogger.log("Pairing session validation failed: invalid QR or not for Alamer", isError = true)
            return Result.failure(Exception("رمز QR غير صالح أو ليس رمز ربط Alamer."))
        }

        if (resolved.isConsumed) {
            DiagnosticLogger.log("Pairing session ${resolved.pairingId} has already been consumed", isError = true)
            return Result.failure(Exception("تم استهلاك رمز الاقتران هذا سابقاً ولا يمكن إعادة استخدامه"))
        }

        if (resolved.isExpired) {
            DiagnosticLogger.log("Pairing session ${resolved.pairingId} expired", isError = true)
            return Result.failure(Exception("QR منتهي"))
        }

        if (resolved.deviceType != "CallerAssistant") {
            DiagnosticLogger.log("Pairing session deviceType mismatch: ${resolved.deviceType}", isError = true)
            return Result.failure(Exception("هذا الرمز لا يمنح صلاحية البدالة (CallerAssistant)"))
        }

        DiagnosticLogger.log("Pairing session ${resolved.pairingId} validated successfully")
        return Result.success(resolved)
    }

    /**
     * Marks the pairing session as consumed so it cannot be used again by any device.
     */
    fun consumePairingSession(pairingId: String): Boolean {
        val session = activeSessions[pairingId] ?: return false
        val consumed = session.copy(isConsumed = true)
        activeSessions[pairingId] = consumed
        if (_currentSession.value?.pairingId == pairingId) {
            _currentSession.value = consumed
        }
        DiagnosticLogger.log("Pairing session $pairingId consumed and retired")
        return true
    }

    fun cancelPairingSession(pairingId: String) {
        activeSessions.remove(pairingId)
        if (_currentSession.value?.pairingId == pairingId) {
            _currentSession.value = null
        }
        DiagnosticLogger.log("Pairing session $pairingId cancelled")
    }

    fun expirePairingSession(pairingId: String) {
        val session = activeSessions[pairingId] ?: return
        val expired = session.copy(expiresAtEpochMs = System.currentTimeMillis() - 1000)
        activeSessions[pairingId] = expired
        if (_currentSession.value?.pairingId == pairingId) {
            _currentSession.value = expired
        }
        DiagnosticLogger.log("Pairing session $pairingId expired manually")
    }
}
