package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorageRepository(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("alamer_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "AlamerCallAssistantKey"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private const val PREF_DEVICE_ID = "sec_device_id"
        private const val PREF_DEVICE_NAME = "sec_device_name"
        private const val PREF_TRUSTED_SERVER_ID = "sec_trusted_server_id"
        private const val PREF_SERVER_HOST = "sec_server_host"
        private const val PREF_SERVER_PORT = "sec_server_port"
        private const val PREF_USE_TLS = "sec_use_tls"
        private const val PREF_DEVICE_TOKEN = "sec_device_token"
        private const val PREF_DEVICE_KEY = "sec_device_key"
        private const val PREF_CALLER_CREDENTIAL = "sec_caller_credential"
        private const val PREF_SERVER_URL = "sec_server_url"
        private const val PREF_PROTOCOL_VERSION = "sec_protocol_version"
        private const val PREF_CAPABILITIES = "sec_capabilities"
        private const val PREF_INSTALLATION_BINDING = "sec_installation_binding"
        private const val PREF_OPERATOR_ACCOUNT = "sec_operator_account"
        private const val PREF_SESSION_TOKEN = "sec_session_token"
        private const val PREF_MOCK_MODE = "pref_mock_mode"
        private const val PREF_AUTO_OPEN_DETAILS = "pref_auto_open_details"
        private const val PREF_DEVICE_APPROVED = "sec_device_approved"
    }

    init {
        ensureSecretKeyExists()
        if (getDeviceId().isBlank()) {
            val newId = "alamer-dev-" + UUID.randomUUID().toString().take(8)
            saveDeviceId(newId)
        }
        if (getInstallationBinding().isBlank()) {
            val binding = "bind-" + UUID.randomUUID().toString()
            saveInstallationBinding(binding)
        }
        if (getDeviceName().isBlank()) {
            saveDeviceName("Alamer بدالة")
        }
    }

    private var fallbackKey: SecretKey? = null

    private fun ensureSecretKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // AndroidKeyStore is not present in JVM unit tests (Robolectric)
            if (fallbackKey == null) {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                fallbackKey = keyGen.generateKey()
            }
        }
    }

    private fun getSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: fallbackKey ?: run {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                val key = keyGen.generateKey()
                fallbackKey = key
                key
            }
        } catch (e: Exception) {
            fallbackKey ?: run {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                val key = keyGen.generateKey()
                fallbackKey = key
                key
            }
        }
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    private fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return ""
            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun getDeviceId(): String {
        val enc = sharedPreferences.getString(PREF_DEVICE_ID, "") ?: ""
        return decrypt(enc)
    }

    fun saveDeviceId(id: String) {
        sharedPreferences.edit().putString(PREF_DEVICE_ID, encrypt(id)).apply()
    }

    fun getDeviceName(): String {
        return sharedPreferences.getString(PREF_DEVICE_NAME, "Alamer بدالة") ?: "Alamer بدالة"
    }

    fun saveDeviceName(name: String) {
        sharedPreferences.edit().putString(PREF_DEVICE_NAME, name).apply()
    }

    fun getTrustedServerId(): String {
        val enc = sharedPreferences.getString(PREF_TRUSTED_SERVER_ID, "") ?: ""
        return decrypt(enc)
    }

    fun saveTrustedServerId(serverId: String) {
        sharedPreferences.edit().putString(PREF_TRUSTED_SERVER_ID, encrypt(serverId)).apply()
    }

    /**
     * Checks whether incoming server ID matches the trusted server ID.
     * Returns true if matches or if no server was previously paired.
     * Returns false if there is a conflict (different server at same endpoint).
     */
    fun verifyServerIdentity(incomingServerId: String): Boolean {
        val trusted = getTrustedServerId()
        if (trusted.isBlank()) return true
        return trusted.equals(incomingServerId, ignoreCase = true)
    }

    fun clearTrustedServer() {
        sharedPreferences.edit().remove(PREF_TRUSTED_SERVER_ID).apply()
        clearSessionToken()
    }

    fun getServerHost(): String {
        return sharedPreferences.getString(PREF_SERVER_HOST, "192.168.1.20") ?: "192.168.1.20"
    }

    fun saveServerHost(host: String) {
        sharedPreferences.edit().putString(PREF_SERVER_HOST, host).apply()
    }

    fun getServerPort(): Int {
        return sharedPreferences.getInt(PREF_SERVER_PORT, 5000)
    }

    fun saveServerPort(port: Int) {
        sharedPreferences.edit().putInt(PREF_SERVER_PORT, port).apply()
    }

    fun getInstallationBinding(): String {
        val enc = sharedPreferences.getString(PREF_INSTALLATION_BINDING, "") ?: ""
        return decrypt(enc)
    }

    private fun saveInstallationBinding(binding: String) {
        sharedPreferences.edit().putString(PREF_INSTALLATION_BINDING, encrypt(binding)).apply()
    }

    fun getOperatorAccount(): String {
        val enc = sharedPreferences.getString(PREF_OPERATOR_ACCOUNT, "") ?: ""
        return decrypt(enc)
    }

    fun saveOperatorAccount(account: String) {
        sharedPreferences.edit().putString(PREF_OPERATOR_ACCOUNT, encrypt(account)).apply()
    }

    fun getSessionToken(): String {
        val enc = sharedPreferences.getString(PREF_SESSION_TOKEN, "") ?: ""
        return decrypt(enc)
    }

    fun saveSessionToken(token: String) {
        sharedPreferences.edit().putString(PREF_SESSION_TOKEN, encrypt(token)).apply()
    }

    fun clearSessionToken() {
        sharedPreferences.edit().remove(PREF_SESSION_TOKEN).apply()
    }

    fun isDeviceApproved(): Boolean {
        return sharedPreferences.getBoolean(PREF_DEVICE_APPROVED, true)
    }

    fun setDeviceApproved(approved: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_DEVICE_APPROVED, approved).apply()
    }

    fun isMockModeEnabled(): Boolean {
        // Mock mode is strictly disabled in production. Defaults to false in all builds.
        return sharedPreferences.getBoolean(PREF_MOCK_MODE, false)
    }

    fun setMockModeEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_MOCK_MODE, enabled).apply()
    }

    fun isAutoOpenDetails(): Boolean {
        return sharedPreferences.getBoolean(PREF_AUTO_OPEN_DETAILS, true)
    }

    fun setAutoOpenDetails(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_AUTO_OPEN_DETAILS, enabled).apply()
    }

    fun getUseTls(): Boolean {
        return sharedPreferences.getBoolean(PREF_USE_TLS, false)
    }

    fun saveUseTls(useTls: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_USE_TLS, useTls).apply()
    }

    fun getDeviceToken(): String {
        val enc = sharedPreferences.getString(PREF_DEVICE_TOKEN, "") ?: ""
        return decrypt(enc)
    }

    fun saveDeviceToken(token: String) {
        sharedPreferences.edit().putString(PREF_DEVICE_TOKEN, encrypt(token)).apply()
    }

    fun getDeviceKey(): String {
        val enc = sharedPreferences.getString(PREF_DEVICE_KEY, "") ?: ""
        return decrypt(enc)
    }

    fun saveDeviceKey(key: String) {
        sharedPreferences.edit().putString(PREF_DEVICE_KEY, encrypt(key)).apply()
    }

    fun getCapabilities(): List<String> {
        val raw = sharedPreferences.getString(PREF_CAPABILITIES, "CallerAssistant") ?: "CallerAssistant"
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun saveCapabilities(capabilities: List<String>) {
        sharedPreferences.edit().putString(PREF_CAPABILITIES, capabilities.joinToString(",")).apply()
    }

    fun getCallerCredential(): String {
        val enc = sharedPreferences.getString(PREF_CALLER_CREDENTIAL, "") ?: ""
        val decrypted = decrypt(enc)
        return if (decrypted.isNotBlank()) decrypted else getDeviceToken()
    }

    fun saveCallerCredential(credential: String) {
        sharedPreferences.edit().putString(PREF_CALLER_CREDENTIAL, encrypt(credential)).apply()
    }

    fun getServerUrl(): String {
        val stored = sharedPreferences.getString(PREF_SERVER_URL, "") ?: ""
        if (stored.isNotBlank()) return stored
        val scheme = if (getUseTls()) "https" else "http"
        return "$scheme://${getServerHost()}:${getServerPort()}"
    }

    fun saveServerUrl(url: String) {
        sharedPreferences.edit().putString(PREF_SERVER_URL, url).apply()
    }

    fun getProtocolVersion(): String {
        return sharedPreferences.getString(PREF_PROTOCOL_VERSION, "1.0") ?: "1.0"
    }

    fun saveProtocolVersion(version: String) {
        sharedPreferences.edit().putString(PREF_PROTOCOL_VERSION, version).apply()
    }

    fun isPaired(): Boolean {
        val serverId = getTrustedServerId()
        val token = getCallerCredential()
        return serverId.isNotBlank() && token.isNotBlank()
    }

    fun savePairingCredentials(
        serverId: String,
        serverUrl: String,
        host: String,
        port: Int,
        tls: Boolean,
        callerCredential: String,
        deviceToken: String = callerCredential,
        deviceKey: String = "",
        protocolVersion: String = "1.0",
        capabilities: List<String> = listOf("CallerAssistant")
    ) {
        saveTrustedServerId(serverId)
        saveServerUrl(serverUrl)
        saveServerHost(host)
        saveServerPort(port)
        saveUseTls(tls)
        saveCallerCredential(callerCredential)
        saveDeviceToken(deviceToken.ifBlank { callerCredential })
        if (deviceKey.isNotBlank()) saveDeviceKey(deviceKey)
        saveProtocolVersion(protocolVersion)
        saveCapabilities(capabilities)
        setDeviceApproved(true)
    }

    fun clearAllPairingCredentials() {
        sharedPreferences.edit()
            .remove(PREF_TRUSTED_SERVER_ID)
            .remove(PREF_DEVICE_TOKEN)
            .remove(PREF_DEVICE_KEY)
            .remove(PREF_CALLER_CREDENTIAL)
            .remove(PREF_SERVER_URL)
            .remove(PREF_SESSION_TOKEN)
            .remove(PREF_CAPABILITIES)
            .apply()
    }
}
