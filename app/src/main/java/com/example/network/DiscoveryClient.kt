package com.example.network

import android.util.Log
import com.example.domain.model.ServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class DiscoveryClient(
    private val discoveryPort: Int = 5051,
    private val expectedProtocolVersion: String = "1.0"
) {

    companion object {
        private const val TAG = "DiscoveryClient"
        const val DISCOVERY_REQUEST = "TALOOLA_POS_DISCOVER_V3"
        const val SERVER_RESPONSE_PREFIX = "TALOOLA_POS_SERVER_V3:"
    }

    /**
     * Broadcasts UDP discovery request on LAN and collects responses.
     */
    suspend fun discoverServers(timeoutMs: Int = 3000): List<ServerInfo> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<ServerInfo>()
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 1000
            }

            val requestBytes = DISCOVERY_REQUEST.toByteArray(Charsets.UTF_8)
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(requestBytes, requestBytes.size, broadcastAddress, discoveryPort)

            socket.send(packet)
            Log.d(TAG, "Sent discovery request to 255.255.255.255:$discoveryPort")

            val startTime = System.currentTimeMillis()
            val buffer = ByteArray(2048)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(responsePacket)
                    val responseStr = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8).trim()

                    if (responseStr.startsWith(SERVER_RESPONSE_PREFIX)) {
                        val payload = responseStr.removePrefix(SERVER_RESPONSE_PREFIX).trim()
                        val senderHost = responsePacket.address.hostAddress ?: ""
                        val serverInfo = parseServerPayload(payload, senderHost)

                        if (serverInfo != null && validateServer(serverInfo)) {
                            if (discovered.none { it.serverId == serverInfo.serverId }) {
                                discovered.add(serverInfo)
                                Log.i(TAG, "Discovered valid TaloolaPos: ${serverInfo.serviceName} at ${serverInfo.host}:${serverInfo.port}")
                            }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Packet timeout, loop continues until total timeoutMs elapsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP discovery error: ${e.message}")
        } finally {
            socket?.close()
        }

        discovered
    }

    /**
     * Validates that the discovered server satisfies protocol and security invariants.
     */
    fun validateServer(server: ServerInfo): Boolean {
        if (server.serverId.isBlank()) {
            Log.w(TAG, "Server rejected: missing serverId")
            return false
        }
        if (server.protocolVersion != expectedProtocolVersion) {
            Log.w(TAG, "Server rejected: protocol mismatch (found ${server.protocolVersion}, expected $expectedProtocolVersion)")
            return false
        }
        if (!server.serviceName.contains("Taloola", ignoreCase = true)) {
            Log.w(TAG, "Server rejected: unrecognized serviceName ${server.serviceName}")
            return false
        }
        return true
    }

    fun getServerInfo(host: String, port: Int = 5000): ServerInfo {
        return ServerInfo(
            serverId = "taloola-server-" + host.replace(".", ""),
            host = host,
            port = port,
            serviceName = "Taloola POS",
            protocolVersion = expectedProtocolVersion,
            sessionId = "",
            sessionActive = true,
            serverVersion = "3.0.0",
            masterDataVersion = "1"
        )
    }

    private fun parseServerPayload(payload: String, senderHost: String): ServerInfo? {
        return try {
            if (payload.startsWith("{")) {
                val json = JSONObject(payload)
                ServerInfo(
                    serverId = json.optString("serverId", "taloola-pos"),
                    host = json.optString("host", senderHost).ifBlank { senderHost },
                    port = json.optInt("port", 5000),
                    serviceName = json.optString("serviceName", "Taloola POS"),
                    protocolVersion = json.optString("protocolVersion", "1.0"),
                    sessionId = json.optString("sessionId", ""),
                    sessionActive = json.optBoolean("sessionActive", true),
                    serverVersion = json.optString("serverVersion", "1.0.0"),
                    masterDataVersion = json.optString("masterDataVersion", "1")
                )
            } else {
                // Key-value format fallback: serverId=xxx;port=5000;...
                val map = payload.split(";").mapNotNull {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                }.toMap()

                ServerInfo(
                    serverId = map["serverId"] ?: "taloola-pos",
                    host = map["host"] ?: senderHost,
                    port = map["port"]?.toIntOrNull() ?: 5000,
                    serviceName = map["serviceName"] ?: "Taloola POS",
                    protocolVersion = map["protocolVersion"] ?: "1.0",
                    sessionId = map["sessionId"] ?: "",
                    sessionActive = map["sessionActive"]?.toBoolean() ?: true,
                    serverVersion = map["serverVersion"] ?: "1.0.0",
                    masterDataVersion = map["masterDataVersion"] ?: "1"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse server payload: $payload", e)
            null
        }
    }
}
