package io.github.magisk317.relay.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Supplies the envelope-encryption key; abstracted behind a fun interface so JVM
 * tests can inject a plain key instead of the Android Keystore.
 */
fun interface AesGcmKeyProvider {
    fun getOrCreate(): SecretKey
}

/** Non-exportable AES-256 key in the Android Keystore, created on first use. */
class AndroidKeystoreKeyProvider(private val alias: String) : AesGcmKeyProvider {
    @Synchronized
    override fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
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

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}

/**
 * Versioned AES-256-GCM envelope cipher ("<version>.<nonce>.<ciphertext>", Base64
 * parts joined by dots), sealed by a non-exportable AndroidKeyStore key. AAD
 * domain-separates ciphertexts so envelopes cannot be transplanted between
 * storage files or replayed for another purpose.
 */
class AesGcmEnvelopeCipher(
    private val keyProvider: AesGcmKeyProvider,
    private val envelopeVersion: String,
) {
    fun encrypt(plaintext: String, aad: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The Keystore key requires randomized encryption, so the nonce must come from the
        // provider instead of the caller; supplying one is rejected as CALLER_NONCE_PROHIBITED.
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreate())
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val nonce = cipher.iv
        require(nonce != null && nonce.size == NONCE_BYTES) { "Unexpected envelope nonce size" }
        return listOf(
            envelopeVersion,
            Base64.getEncoder().encodeToString(nonce),
            Base64.getEncoder().encodeToString(ciphertext),
        ).joinToString(ENVELOPE_SEPARATOR)
    }

    fun decrypt(envelope: String, aad: ByteArray): String {
        val parts = envelope.split(ENVELOPE_SEPARATOR, limit = ENVELOPE_PARTS)
        require(parts.size == ENVELOPE_PARTS && parts[0] == envelopeVersion) {
            "Unsupported envelope version"
        }
        val nonce = Base64.getDecoder().decode(parts[1])
        require(nonce.size == NONCE_BYTES) { "Invalid envelope nonce" }
        val ciphertext = Base64.getDecoder().decode(parts[2])
        require(ciphertext.size >= TAG_BYTES) { "Invalid envelope ciphertext" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreate(), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun isCurrentEnvelope(value: String): Boolean =
        value.startsWith("$envelopeVersion$ENVELOPE_SEPARATOR")

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENVELOPE_SEPARATOR = "."
        const val ENVELOPE_PARTS = 3
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / Byte.SIZE_BITS
    }
}
