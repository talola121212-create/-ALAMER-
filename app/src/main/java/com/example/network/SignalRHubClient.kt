package com.example.network

import android.util.Log
import com.example.domain.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SignalRHubClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "SignalRHubClient"
        private const val RECORD_SEPARATOR = "\u001E"
    }

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val pendingInvocations = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val listeners = ConcurrentHashMap<String, (JSONObject) -> Unit>()

    private var pingJob: Job? = null
    private var isHandshakeComplete = false

    fun registerListener(target: String, callback: (JSONObject) -> Unit) {
        listeners[target] = callback
    }

    fun connect(url: String, onConnected: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        disconnect()

        _connectionState.value = ConnectionState.CONNECTING
        Log.i(TAG, "Connecting to SignalR hub at $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened, sending SignalR JSON handshake...")
                // SignalR handshake
                val handshake = "{\"protocol\":\"json\",\"version\":1}$RECORD_SEPARATOR"
                ws.send(handshake)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text, onConnected)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing: $code / $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = ConnectionState.SERVER_UNAVAILABLE
                cleanup()
                onError?.invoke(t.message ?: "Connection failed")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                cleanup()
            }
        })
    }

    private fun handleIncomingMessage(rawText: String, onConnected: (() -> Unit)?) {
        val messages = rawText.split(RECORD_SEPARATOR)
        for (msg in messages) {
            val trimmed = msg.trim()
            if (trimmed.isEmpty()) continue

            try {
                val json = JSONObject(trimmed)

                // Handshake response is empty object or contains error
                if (!isHandshakeComplete) {
                    if (json.has("error")) {
                        val error = json.getString("error")
                        Log.e(TAG, "Handshake failed: $error")
                        _connectionState.value = ConnectionState.PROTOCOL_MISMATCH
                        disconnect()
                        return
                    }
                    isHandshakeComplete = true
                    _connectionState.value = ConnectionState.CONNECTED
                    startPingLoop()
                    Log.i(TAG, "SignalR Handshake completed successfully!")
                    onConnected?.invoke()
                    continue
                }

                val type = json.optInt("type", -1)
                when (type) {
                    1 -> { // Invocation from server to client
                        val target = json.optString("target")
                        val listener = listeners[target]
                        listener?.invoke(json)
                    }
                    3 -> { // Completion of client invocation
                        val invocationId = json.optString("invocationId")
                        val deferred = pendingInvocations.remove(invocationId)
                        if (deferred != null) {
                            if (json.has("error") && !json.isNull("error")) {
                                deferred.completeExceptionally(Exception(json.getString("error")))
                            } else {
                                val result = if (json.has("result")) json.get("result").toString() else "{}"
                                deferred.complete(result)
                            }
                        }
                    }
                    6 -> { // Ping from server
                        // Responded by ping interval or keep-alive
                    }
                    7 -> { // Close message
                        val error = json.optString("error", "Server closed connection")
                        Log.w(TAG, "Server requested close: $error")
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing SignalR message: $trimmed", e)
            }
        }
    }

    suspend fun invoke(target: String, vararg args: Any?, timeoutMs: Long = 10000): String {
        val ws = webSocket
        if (ws == null || !isHandshakeComplete) {
            throw IllegalStateException("SignalR client is not connected to /posHub")
        }

        val invocationId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pendingInvocations[invocationId] = deferred

        val json = JSONObject().apply {
            put("type", 1)
            put("invocationId", invocationId)
            put("target", target)
            val argsArray = JSONArray()
            for (arg in args) {
                argsArray.put(arg ?: JSONObject.NULL)
            }
            put("arguments", argsArray)
        }

        val payload = json.toString() + RECORD_SEPARATOR
        ws.send(payload)

        return withTimeout(timeoutMs) {
            deferred.await()
        }
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && isHandshakeComplete) {
                delay(15000)
                try {
                    webSocket?.send("{\"type\":6}$RECORD_SEPARATOR")
                } catch (e: Exception) {
                    Log.w(TAG, "Ping failed: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        cleanup()
        try {
            webSocket?.close(1000, "App disconnected")
        } catch (ignored: Exception) {}
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun cleanup() {
        isHandshakeComplete = false
        pingJob?.cancel()
        pingJob = null
        pendingInvocations.values.forEach { it.cancel() }
        pendingInvocations.clear()
    }
}
