package io.github.magisk317.relay.backup.webdav

import android.content.Context
import android.content.SharedPreferences
import io.github.magisk317.relay.android.common.utils.XLog
import java.util.Base64
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WebDavConfigStore {
    private const val PREFS_NAME = "webdav_config_prefs"
    private const val KEY_CONFIG = "webdav_config"
    private const val AAD_VERSION = "v2"

    private val json = Json { ignoreUnknownKeys = true }
    private val cipher: WebDavConfigCipher = AesGcmWebDavConfigCipher(AndroidKeystoreWebDavKeyProvider)

    fun getConfig(context: Context): WebDavConfig? {
        return persistence(context).read().also { config ->
            if (config == null && getPrefs(context).contains(KEY_CONFIG)) {
                XLog.w("WebDAV config could not be decrypted; stored value was preserved.")
            }
        }
    }

    fun saveConfig(context: Context, config: WebDavConfig): Boolean {
        val saved = persistence(context).write(config)
        if (!saved) XLog.w("WebDAV config could not be stored securely.")
        return saved
    }

    fun removeConfig(context: Context) {
        getPrefs(context).edit().remove(KEY_CONFIG).apply()
    }

    fun observeConfig(context: Context): Flow<WebDavConfig?> {
        val appContext = context.applicationContext ?: context
        return callbackFlow {
            val prefs = getPrefs(appContext)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == KEY_CONFIG) trySend(getConfig(appContext))
            }
            trySend(getConfig(appContext))
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()
    }

    private fun persistence(context: Context): WebDavConfigPersistence {
        val appContext = context.applicationContext ?: context
        val prefs = getPrefs(appContext)
        return WebDavConfigPersistence(
            cipher = cipher,
            aad = buildAad(appContext.packageName),
            readValue = { prefs.getString(KEY_CONFIG, null) },
            writeValue = { value -> prefs.edit().putString(KEY_CONFIG, value).commit() },
        )
    }

    private fun buildAad(packageName: String): ByteArray =
        "$packageName|$PREFS_NAME|$KEY_CONFIG|$AAD_VERSION".toByteArray(Charsets.UTF_8)

    private fun getPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

internal class WebDavConfigPersistence(
    private val cipher: WebDavConfigCipher,
    private val aad: ByteArray,
    private val readValue: () -> String?,
    private val writeValue: (String) -> Boolean,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): WebDavConfig? {
        val stored = runCatching(readValue).getOrNull() ?: return null
        if (cipher.isCurrentEnvelope(stored)) {
            return decryptAndDecode(stored)
        }

        val legacyPlaintext = runCatching { LegacyWebDavConfigCodec.decrypt(stored) }.getOrNull() ?: return null
        val config = decode(legacyPlaintext) ?: return null
        val migrated = runCatching { cipher.encrypt(legacyPlaintext, aad) }.getOrNull() ?: return null
        if (!runCatching { writeValue(migrated) }.getOrDefault(false)) return null
        return config
    }

    fun write(config: WebDavConfig): Boolean {
        val plaintext = runCatching { json.encodeToString(config) }.getOrNull() ?: return false
        val encrypted = runCatching { cipher.encrypt(plaintext, aad) }.getOrNull() ?: return false
        return runCatching { writeValue(encrypted) }.getOrDefault(false)
    }

    private fun decryptAndDecode(stored: String): WebDavConfig? {
        val plaintext = runCatching { cipher.decrypt(stored, aad) }.getOrNull() ?: return null
        return decode(plaintext)
    }

    private fun decode(plaintext: String): WebDavConfig? = try {
        json.decodeFromString<WebDavConfig>(plaintext)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: SerializationException) {
        null
    }
}

internal object LegacyWebDavConfigCodec {
    private const val LEGACY_KEY = "xinyi-relay-webdav-key-2026"

    fun decrypt(encryptedData: String): String {
        val encrypted = Base64.getDecoder().decode(encryptedData)
        val decrypted = CharArray(encrypted.size)
        for (index in encrypted.indices) {
            decrypted[index] = (encrypted[index].toInt() xor LEGACY_KEY[index % LEGACY_KEY.length].code).toChar()
        }
        return String(decrypted)
    }
}
