package com.github.magisk317.smscode.forwarder.utils.sender

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelegramUtilsTest {

    private fun String.escapeMarkdownV2(): String {
        return this.replace(Regex("""([_*\[\]()~`>#+\-=|{}.!\\])""")) { "\\${it.value}" }
    }

    @Test
    fun testEscape() {
        val input = "Hello _*[]()~`>#+-=|{}.!\\"
        val expected = "Hello \\_\\*\\[\\]\\(\\)\\~\\`\\>\\#\\+\\-\\=\\|\\{\\}\\.\\!\\\\"
        assertEquals(expected, input.escapeMarkdownV2())
    }
}
