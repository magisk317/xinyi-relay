package io.github.magisk317.relay.backup.webdav

import io.github.magisk317.relay.security.AndroidKeystoreKeyProvider
import io.github.magisk317.relay.security.AesGcmEnvelopeCipher
import javax.crypto.SecretKey

internal interface WebDavConfigCipher {
    fun encrypt(plaintext: String, aad: ByteArray): String

    fun decrypt(envelope: String, aad: ByteArray): String

    fun isCurrentEnvelope(value: String): Boolean
}

internal fun interface WebDavSecretKeyProvider {
    fun getOrCreate(): SecretKey
}

/**
 * Thin facade over the shared [AesGcmEnvelopeCipher]; the WebDAV envelope stays
 * "v2" so previously stored configuration decrypts without migration.
 */
internal class AesGcmWebDavConfigCipher(
    private val keyProvider: WebDavSecretKeyProvider,
) : WebDavConfigCipher {
    private val delegate = AesGcmEnvelopeCipher(
        keyProvider = { keyProvider.getOrCreate() },
        envelopeVersion = "v2",
    )

    override fun encrypt(plaintext: String, aad: ByteArray): String = delegate.encrypt(plaintext, aad)

    override fun decrypt(envelope: String, aad: ByteArray): String = delegate.decrypt(envelope, aad)

    override fun isCurrentEnvelope(value: String): Boolean = delegate.isCurrentEnvelope(value)
}

internal object AndroidKeystoreWebDavKeyProvider : WebDavSecretKeyProvider {
    private val provider = AndroidKeystoreKeyProvider("io.github.magisk317.xinyi.relay.webdav_config.v2")

    override fun getOrCreate(): SecretKey = provider.getOrCreate()
}
