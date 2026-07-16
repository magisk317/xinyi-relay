package io.github.magisk317.relay.backup.webdav

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface WebDavConfigCipher {
    fun encrypt(plaintext: String, aad: ByteArray): String

    fun decrypt(envelope: String, aad: ByteArray): String

    fun isCurrentEnvelope(value: String): Boolean
}

internal fun interface WebDavSecretKeyProvider {
    fun getOrCreate(): SecretKey
}

internal class AesGcmWebDavConfigCipher(
    private val keyProvider: WebDavSecretKeyProvider,
    private val secureRandom: SecureRandom = SecureRandom(),
) : WebDavConfigCipher {
    override fun encrypt(plaintext: String, aad: ByteArray): String {
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreate(), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return listOf(
            ENVELOPE_VERSION,
            Base64.getEncoder().encodeToString(nonce),
            Base64.getEncoder().encodeToString(ciphertext),
        ).joinToString(ENVELOPE_SEPARATOR)
    }

    override fun decrypt(envelope: String, aad: ByteArray): String {
        val parts = envelope.split(ENVELOPE_SEPARATOR, limit = ENVELOPE_PARTS)
        require(parts.size == ENVELOPE_PARTS && parts[0] == ENVELOPE_VERSION) {
            "Unsupported WebDAV config envelope"
        }
        val nonce = Base64.getDecoder().decode(parts[1])
        require(nonce.size == NONCE_BYTES) { "Invalid WebDAV config nonce" }
        val ciphertext = Base64.getDecoder().decode(parts[2])
        require(ciphertext.size >= TAG_BYTES) { "Invalid WebDAV config ciphertext" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreate(), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    override fun isCurrentEnvelope(value: String): Boolean =
        value.startsWith("$ENVELOPE_VERSION$ENVELOPE_SEPARATOR")

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENVELOPE_VERSION = "v2"
        const val ENVELOPE_SEPARATOR = "."
        const val ENVELOPE_PARTS = 3
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / Byte.SIZE_BITS
    }
}

internal object AndroidKeystoreWebDavKeyProvider : WebDavSecretKeyProvider {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "io.github.magisk317.xinyi.relay.webdav_config.v2"

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
