package com.trybsportowy.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The ONLY accessor for the Bearer secret and server URL (CLAUDE.md §4.6, §1.4).
 * Backed by EncryptedSharedPreferences (AES256). The secret never leaves this
 * class as a log line, a BuildConfig field, or a git-tracked file.
 */
class SecretsStore(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSecret(secret: String) = prefs.edit().putString(K_SECRET, secret).apply()
    fun getSecret(): String? = prefs.getString(K_SECRET, null)
    fun clearSecret() = prefs.edit().remove(K_SECRET).apply()
    fun hasSecret(): Boolean = !getSecret().isNullOrBlank()

    fun saveServerUrl(url: String) = prefs.edit().putString(K_URL, url).apply()
    fun getServerUrl(): String? = prefs.getString(K_URL, null)

    /** Server URL with a guaranteed non-null value (falls back to the default). */
    fun serverUrlOrDefault(): String = getServerUrl()?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER_URL

    fun markSecretInvalid() = prefs.edit().putBoolean(K_INVALID, true).apply()
    fun clearSecretInvalid() = prefs.edit().putBoolean(K_INVALID, false).apply()
    fun isSecretInvalid(): Boolean = prefs.getBoolean(K_INVALID, false)

    companion object {
        private const val K_SECRET = "android_api_secret"
        private const val K_URL = "server_url"
        private const val K_INVALID = "secret_invalid"
        const val DEFAULT_SERVER_URL = "https://aiserver.tail198ba5.ts.net"
    }
}
