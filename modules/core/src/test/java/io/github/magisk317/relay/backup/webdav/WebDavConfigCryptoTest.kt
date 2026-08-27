package io.github.magisk317.relay.backup.webdav

import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WebDavConfigCryptoTest {
    private val key = SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
    private val cipher = AesGcmWebDavConfigCipher(
        keyProvider = WebDavSecretKeyProvider { key },
    )
    private val aad = "test.package|webdav_config_prefs|webdav_config|v2".toByteArray()

    @Test
    fun `aes gcm envelope round trips and binds aad`() {
        val encrypted = cipher.encrypt("secret", aad)

        assertTrue(cipher.isCurrentEnvelope(encrypted))
        assertEquals("secret", cipher.decrypt(encrypted, aad))
        assertThrows<Exception> {
            cipher.decrypt(encrypted, "other.package|webdav_config_prefs|webdav_config|v2".toByteArray())
        }
    }

    @Test
    fun `tampered ciphertext fails closed without replacing stored value`() {
        var stored = cipher.encrypt(Json.encodeToString(config()), aad)
        val original = stored
        val parts = stored.split(".", limit = 3).toMutableList()
        val ciphertext = Base64.getDecoder().decode(parts[2]).also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        }
        parts[2] = Base64.getEncoder().encodeToString(ciphertext)
        stored = parts.joinToString(".")
        val tampered = stored
        val persistence = persistence(read = { stored }, write = { value -> stored = value; true })

        assertNull(persistence.read())
        assertEquals(tampered, stored)
        assertFalse(original == stored)
    }

    @Test
    fun `legacy value migrates only after authenticated envelope is persisted`() {
        var stored = legacyEncrypt(Json.encodeToString(config()))
        val legacy = stored
        val persistence = persistence(read = { stored }, write = { value -> stored = value; true })

        assertEquals(config(), persistence.read())
        assertFalse(legacy == stored)
        assertTrue(cipher.isCurrentEnvelope(stored))
        assertEquals(config(), persistence.read())
    }

    @Test
    fun `keystore unavailable preserves recoverable legacy and rejects writes`() {
        var stored = legacyEncrypt(Json.encodeToString(config()))
        val legacy = stored
        val unavailableCipher = AesGcmWebDavConfigCipher(
            keyProvider = WebDavSecretKeyProvider { error("keystore unavailable") },
        )
        val persistence = WebDavConfigPersistence(
            cipher = unavailableCipher,
            aad = aad,
            readValue = { stored },
            writeValue = { value -> stored = value; true },
        )

        assertNull(persistence.read())
        assertEquals(legacy, stored)
        assertFalse(persistence.write(config().copy(password = "new-password")))
        assertEquals(legacy, stored)
    }

    @Test
    fun `failed migration commit preserves legacy value`() {
        var stored = legacyEncrypt(Json.encodeToString(config()))
        val legacy = stored
        val persistence = persistence(read = { stored }, write = { false })

        assertNull(persistence.read())
        assertEquals(legacy, stored)
    }

    private fun persistence(
        read: () -> String?,
        write: (String) -> Boolean,
    ) = WebDavConfigPersistence(
        cipher = cipher,
        aad = aad,
        readValue = read,
        writeValue = write,
    )

    private fun config() = WebDavConfig(
        serverUrl = "https://dav.example.test/",
        username = "alice",
        password = "correct horse battery staple",
        remotePath = "/relay/",
    )

    private fun legacyEncrypt(plaintext: String): String {
        val key = "xinyi-relay-webdav-key-2026"
        val encrypted = ByteArray(plaintext.length)
        for (index in plaintext.indices) {
            encrypted[index] = (plaintext[index].code xor key[index % key.length].code).toByte()
        }
        return Base64.getEncoder().encodeToString(encrypted)
    }
}
