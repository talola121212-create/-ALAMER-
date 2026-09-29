package com.example.security

import com.example.domain.model.DiagnosticLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object DiagnosticLogger {

    private const val MAX_LOGS = 100
    private val buffer = ConcurrentLinkedQueue<DiagnosticLogEntry>()
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    init {
        log("Diagnostic system initialized")
    }

    fun log(rawMessage: String, isError: Boolean = false) {
        val sanitized = sanitize(rawMessage)
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date())
        val entry = DiagnosticLogEntry(
            id = System.nanoTime(),
            timestamp = timestamp,
            message = sanitized,
            isError = isError
        )

        buffer.add(entry)
        while (buffer.size > MAX_LOGS) {
            buffer.poll()
        }
        _logs.value = buffer.toList()
    }

    fun clear() {
        buffer.clear()
        _logs.value = emptyList()
        log("Logs cleared by operator")
    }

    /**
     * Ensures NO PINs, tokens, passwords, or credentials ever leak to logs.
     */
    private fun sanitize(input: String): String {
        return input
            .replace(Regex("(?i)pin[\"':= ]+([0-9a-zA-Z]+)"), "pin=******")
            .replace(Regex("(?i)password[\"':= ]+([^,\\s]+)"), "password=******")
            .replace(Regex("(?i)token[\"':= ]+([^,\\s]+)"), "token=***")
            .replace(Regex("(?i)sessionToken[\"':= ]+([^,\\s]+)"), "sessionToken=***")
            .replace(Regex("(?i)pairingCode[\"':= ]+([0-9a-zA-Z]+)"), "pairingCode=******")
    }
}
