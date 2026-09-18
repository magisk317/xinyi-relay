package io.github.magisk317.relay.sender

import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EmailOAuthCredentialCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
    private val cipher = AesGcmEmailOAuthCredentialCipher(keyProvider = { key })
    private val aad = "test.package|xinyi_oauth2_credentials|oauth2_cred|v1".toByteArray()

    @Test
    fun envelopeRoundTripsAndBindsAad() {
        val encrypted = cipher.encrypt("secret", aad)

        assertTrue(cipher.isCurrentEnvelope(encrypted))
        assertEquals("secret", cipher.decrypt(encrypted, aad))
        assertThrows<Exception> {
            cipher.decrypt(encrypted, "other.package|xinyi_oauth2_credentials|oauth2_cred|v1".toByteArray())
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val encrypted = cipher.encrypt("secret", aad)
        val parts = encrypted.split(".", limit = 3).toMutableList()
        val ciphertext = Base64.getDecoder().decode(parts[2]).also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        }
        parts[2] = Base64.getEncoder().encodeToString(ciphertext)

        assertThrows<Exception> {
            cipher.decrypt(parts.joinToString("."), aad)
        }
    }

    @Test
    fun malformedEnvelopesFailClosed() {
        assertThrows<IllegalArgumentException> { cipher.decrypt("plain-legacy-value", aad) }
        assertThrows<IllegalArgumentException> { cipher.decrypt("v9.aaaa.bbbb", aad) }
        assertFalse(cipher.isCurrentEnvelope("plain-legacy-value"))
    }

    @Test
    fun credentialsJsonRoundTripsThroughCipher() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val credentials = EmailOAuthCredentials(
            credentialId = "sender-1",
            clientId = "client",
            tenantId = "consumers",
            refreshToken = "refresh-token",
            accessToken = "access-token",
            tokenExpiryMs = 1234L,
            userEmail = "user@example.com",
            scopes = listOf("smtp", "offline_access"),
        )

        val encrypted = cipher.encrypt(json.encodeToString(credentials), aad)
        val restored = json.decodeFromString<EmailOAuthCredentials>(cipher.decrypt(encrypted, aad))

        assertEquals(credentials, restored)
        assertFalse(encrypted.contains("refresh-token"))
    }
}
