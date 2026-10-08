package io.github.magisk317.relay.desktop.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the bind payload the advanced page hands to the QR renderer: it must
 * stay the deep link the Android app parses, with both parameters encoded.
 */
class QrCodeTest {

    @Test
    fun bindValue_encodesTheCodeAndTheConsoleUrl() {
        assertEquals(
            "xinyi-relay://bind?code=AB12-CD34&base_url=https%3A%2F%2Frelay.example.com",
            buildBindQrValue("AB12-CD34", "https://relay.example.com"),
        )
    }

    @Test
    fun bindValue_dropsTrailingSlashesFromTheBaseUrl() {
        assertEquals(
            buildBindQrValue("CODE", "http://127.0.0.1:8080"),
            buildBindQrValue("CODE", "http://127.0.0.1:8080///"),
        )
    }

    @Test
    fun bindValue_percentEncodesReservedCharacters() {
        val value = buildBindQrValue("a b/c", "https://relay.example.com/base path")
        assertEquals(
            "xinyi-relay://bind?code=a+b%2Fc&base_url=https%3A%2F%2Frelay.example.com%2Fbase+path",
            value,
        )
    }

    @Test
    fun bindValue_keepsAnEmptyBaseUrlWhenNoProfileIsSelected() {
        assertEquals(
            "xinyi-relay://bind?code=CODE&base_url=",
            buildBindQrValue("CODE", ""),
        )
    }
}
