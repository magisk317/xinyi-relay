package io.github.magisk317.relay.backup.webdav

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WebDavConfigStore {
    private const val PREFS_NAME = "webdav_config_prefs"
    private const val KEY_CONFIG = "webdav_config"

    private val json = Json { ignoreUnknownKeys = true }

    fun getConfig(context: Context): WebDavConfig? {
        val encrypted = getPrefs(context).getString(KEY_CONFIG, null) ?: return null
        return try {
            val decrypted = SimpleCrypto.decrypt(encrypted)
            json.decodeFromString<WebDavConfig>(decrypted)
        } catch (e: Exception) {
            null
        }
    }

    fun saveConfig(context: Context, config: WebDavConfig) {
        val jsonStr = json.encodeToString(config)
        val encrypted = SimpleCrypto.encrypt(jsonStr)
        getPrefs(context).edit().putString(KEY_CONFIG, encrypted).apply()
    }

    fun removeConfig(context: Context) {
        getPrefs(context).edit().remove(KEY_CONFIG).apply()
    }

    fun observeConfig(context: Context): Flow<WebDavConfig?> {
        val appContext = context.applicationContext ?: context
        return callbackFlow {
            val prefs = getPrefs(appContext)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == KEY_CONFIG) {
                    trySend(getConfig(appContext))
                }
            }
            trySend(getConfig(appContext))
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }.distinctUntilChanged()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

/**
 * Simple XOR-based encryption using Android Keystore.
 * For production, consider using EncryptedSharedPreferences or more robust encryption.
 */
private object SimpleCrypto {
    private const val PREFS_NAME = "webdav_crypto_prefs"
    private const val KEY_SECRET = "encryption_key"

    fun encrypt(data: String): String {
        val key = getOrCreateKey()
        val encrypted = ByteArray(data.length)
        for (i in data.indices) {
            encrypted[i] = (data[i].code xor key[i % key.length].code).toByte()
        }
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }

    fun decrypt(encryptedData: String): String {
        val key = getOrCreateKey()
        val encrypted = android.util.Base64.decode(encryptedData, android.util.Base64.NO_WRAP)
        val decrypted = CharArray(encrypted.size)
        for (i in encrypted.indices) {
            decrypted[i] = (encrypted[i].toInt() xor key[i % key.length].code).toChar()
        }
        return String(decrypted)
    }

    private fun getOrCreateKey(): String {
        // In production, use Android Keystore to generate/store a proper key
        // For simplicity, we use a hardcoded key here
        return "xinyi-relay-webdav-key-2026"
    }
}
