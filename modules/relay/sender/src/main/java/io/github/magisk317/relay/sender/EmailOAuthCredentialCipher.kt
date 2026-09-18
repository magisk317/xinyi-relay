package io.github.magisk317.relay.sender

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM cipher for OAuth2 credentials at rest, backed by a non-exportable
 * AndroidKeyStore key. Mirrors the proven WebDAV config cipher envelope in
 * modules/core (versioned "v1.<nonce>.<ciphertext>" with AAD domain separation);
 * kept separate so the published WebDAV chain stays untouched before release.
 */
internal class AesGcmEmailOAuthCredentialCipher(
    private val keyProvider: EmailOAuthSecretKeyProvider,
) {
    fun encrypt(plaintext: String, aad: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The Keystore key requires randomized encryption, so the nonce must come from the
        // provider instead of the caller; supplying one is rejected as CALLER_NONCE_PROHIBITED.
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreate())
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val nonce = cipher.iv
        require(nonce != null && nonce.size == NONCE_BYTES) { "Unexpected OAuth2 credential nonce size" }
        return listOf(
            ENVELOPE_VERSION,
            Base64.getEncoder().encodeToString(nonce),
            Base64.getEncoder().encodeToString(ciphertext),
        ).joinToString(ENVELOPE_SEPARATOR)
    }

    fun decrypt(envelope: String, aad: ByteArray): String {
        val parts = envelope.split(ENVELOPE_SEPARATOR, limit = ENVELOPE_PARTS)
        require(parts.size == ENVELOPE_PARTS && parts[0] == ENVELOPE_VERSION) {
            "Unsupported OAuth2 credential envelope"
        }
        val nonce = Base64.getDecoder().decode(parts[1])
        require(nonce.size == NONCE_BYTES) { "Invalid OAuth2 credential nonce" }
        val ciphertext = Base64.getDecoder().decode(parts[2])
        require(ciphertext.size >= TAG_BYTES) { "Invalid OAuth2 credential ciphertext" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreate(), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun isCurrentEnvelope(value: String): Boolean =
        value.startsWith("$ENVELOPE_VERSION$ENVELOPE_SEPARATOR")

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENVELOPE_VERSION = "v1"
        const val ENVELOPE_SEPARATOR = "."
        const val ENVELOPE_PARTS = 3
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / Byte.SIZE_BITS
    }
}

/**
 * Supplies the credential-encryption key; abstracted behind a fun interface so
 * JVM tests can inject a plain key instead of the Android Keystore.
 */
internal fun interface EmailOAuthSecretKeyProvider {
    fun getOrCreate(): SecretKey
}

/** Non-exportable AES-256 key in the Android Keystore, created on first use. */
internal object AndroidKeystoreEmailOAuthKeyProvider : EmailOAuthSecretKeyProvider {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "io.github.magisk317.xinyi.relay.email_oauth2.v1"

    @Synchronized
    override fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
