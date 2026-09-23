package io.github.magisk317.relay.sender

import android.content.Context
import android.content.SharedPreferences
import io.github.magisk317.relay.security.AndroidKeystoreKeyProvider
import io.github.magisk317.relay.security.AesGcmEnvelopeCipher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Android-backed [EmailOAuthCredentialStore]. Each value is an AES-256-GCM
 * envelope ([AesGcmEnvelopeCipher]) sealed by a non-exportable
 * AndroidKeyStore key, so secrets never appear in plaintext at rest. Preference
 * keys carry only the non-secret [EmailOAuthCredentials.credentialId].
 */
class AndroidEmailOAuthCredentialStore(
    context: Context,
    private val prefs: SharedPreferences = createDefaultPrefs(context),
) : EmailOAuthCredentialStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val cipher = AesGcmEnvelopeCipher(
        keyProvider = AndroidKeystoreKeyProvider(EMAIL_OAUTH2_KEY_ALIAS),
        envelopeVersion = "v1",
    )
    // Domain-separates ciphertexts so envelopes cannot be transplanted between
    // preference files or replayed for another purpose.
    private val aad = "${context.packageName}|$PREFS_FILE|oauth2_cred|v1".toByteArray()

    override fun save(credentials: EmailOAuthCredentials) {
        prefs.edit()
            .putString(keyFor(credentials.credentialId), cipher.encrypt(json.encodeToString(credentials), aad))
            .apply()
    }

    override fun load(credentialId: String): EmailOAuthCredentials? {
        val key = keyFor(credentialId)
        val raw = prefs.getString(key, null) ?: return null
        // A Keystore key wiped by the system (or corrupted data) fails closed:
        // drop the stale envelope so the sender falls back to a fresh device-code
        // grant instead of surfacing unrecoverable credentials.
        val decrypted = runCatching { cipher.decrypt(raw, aad) }.getOrElse {
            prefs.edit().remove(key).apply()
            return null
        }
        return runCatching { json.decodeFromString<EmailOAuthCredentials>(decrypted) }.getOrNull()
    }

    override fun updateTokens(
        credentialId: String,
        accessToken: String,
        refreshToken: String,
        tokenExpiryMs: Long,
    ) {
        val existing = load(credentialId) ?: return
        save(
            existing.copy(
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenExpiryMs = tokenExpiryMs,
            ),
        )
    }

    override fun delete(credentialId: String) {
        prefs.edit().remove(keyFor(credentialId)).apply()
    }

    override fun exists(credentialId: String): Boolean {
        return prefs.contains(keyFor(credentialId))
    }

    private fun keyFor(credentialId: String): String = "oauth2_cred_$credentialId"

    companion object {
        private const val PREFS_FILE = "xinyi_oauth2_credentials"
        private const val EMAIL_OAUTH2_KEY_ALIAS = "io.github.magisk317.xinyi.relay.email_oauth2.v1"

        fun createDefaultPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }
}
