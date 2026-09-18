package io.github.magisk317.relay.sender

import io.github.magisk317.relay.security.AesGcmEnvelopeCipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EmailOAuthEnvelopeCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
    private val cipher = AesGcmEnvelopeCipher(keyProvider = { key }, envelopeVersion = "v1")
    private val aad = "test.package|xinyi_oauth2_credentials|oauth2_cred|v1".toByteArray()

    @Test
    fun credentialsJsonRoundTripsThroughEnvelope() {
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
        assertThrows<Exception> {
            cipher.decrypt(encrypted, "other.package|xinyi_oauth2_credentials|oauth2_cred|v1".toByteArray())
        }
    }
}
