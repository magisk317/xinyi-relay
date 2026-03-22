package io.github.magisk317.relay.platform.sender

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
