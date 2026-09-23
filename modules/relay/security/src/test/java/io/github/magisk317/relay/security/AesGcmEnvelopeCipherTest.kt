package io.github.magisk317.relay.security

import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AesGcmEnvelopeCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
    private val v1 = AesGcmEnvelopeCipher(keyProvider = { key }, envelopeVersion = "v1")
    private val v2 = AesGcmEnvelopeCipher(keyProvider = { key }, envelopeVersion = "v2")
    private val aad = "test.package|prefs|key|v1".toByteArray()

    @Test
    fun envelopeRoundTripsAndBindsAad() {
        val encrypted = v1.encrypt("secret", aad)

        assertTrue(v1.isCurrentEnvelope(encrypted))
        assertEquals("secret", v1.decrypt(encrypted, aad))
        assertThrows<Exception> {
            v1.decrypt(encrypted, "other.package|prefs|key|v1".toByteArray())
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val encrypted = v1.encrypt("secret", aad)
        val parts = encrypted.split(".", limit = 3).toMutableList()
        val ciphertext = Base64.getDecoder().decode(parts[2]).also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        }
        parts[2] = Base64.getEncoder().encodeToString(ciphertext)

        assertThrows<Exception> {
            v1.decrypt(parts.joinToString("."), aad)
        }
    }

    @Test
    fun malformedEnvelopesFailClosed() {
        assertThrows<IllegalArgumentException> { v1.decrypt("plain-legacy-value", aad) }
        assertThrows<IllegalArgumentException> { v1.decrypt("v9.aaaa.bbbb", aad) }
        assertFalse(v1.isCurrentEnvelope("plain-legacy-value"))
    }

    @Test
    fun envelopeVersionsAreNotInterchangeable() {
        val encrypted = v1.encrypt("secret", aad)
        assertTrue(v2.isCurrentEnvelope(encrypted).not())
        assertThrows<IllegalArgumentException> { v2.decrypt(encrypted, aad) }
    }
}
