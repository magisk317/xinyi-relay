package com.github.magisk317.smscode.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebUiTlsManagerTest {

    @Test
    fun generateRandomCredential_defaultLengthAndCharset() {
        val token = WebUiTlsManager.generateRandomCredential()
        assertEquals(8, token.length)
        assertTrue(token.matches(Regex("^[0-9A-Za-z_]{8}$")))
    }

    @Test
    fun generateRandomCredential_customLength() {
        val token = WebUiTlsManager.generateRandomCredential(16)
        assertEquals(16, token.length)
        assertTrue(token.matches(Regex("^[0-9A-Za-z_]{16}$")))
    }
}
