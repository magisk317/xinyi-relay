package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.NtfySetting
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class NtfyUtilsTest {

    @Test
    fun buildPublishUrl_normalizesServerAndEncodesTopic() {
        val url = NtfyUtils.buildPublishUrl(
            server = "https://ntfy.sh/",
            topic = "team/alerts high",
        )
        assertEquals("https://ntfy.sh/team%2Falerts%20high", url)
    }

    @Test
    fun normalizePriority_acceptsOnlyOneToFive() {
        assertEquals("3", NtfyUtils.normalizePriority(""))
        assertEquals("3", NtfyUtils.normalizePriority("0"))
        assertEquals("3", NtfyUtils.normalizePriority("6"))
        assertEquals("3", NtfyUtils.normalizePriority("abc"))
        assertEquals("5", NtfyUtils.normalizePriority("5"))
    }

    @Test
    fun buildHeaders_addsBearerAndTagsWhenConfigured() {
        val headers = NtfyUtils.buildHeaders(
            setting = NtfySetting(
                title = "",
                priority = "5",
                tags = "sms, android , ,relay",
                token = "abc123",
            ),
            msgInfo = MsgInfo(
                type = "sms",
                from = "10086",
                content = "test",
                date = Date(),
                simInfo = "SIM1",
            ),
        )

        assertEquals("信息驿站: 10086", headers["Title"])
        assertEquals("5", headers["Priority"])
        assertEquals("sms,android,relay", headers["Tags"])
        assertEquals("Bearer abc123", headers["Authorization"])
    }

    @Test
    fun buildHeaders_omitsOptionalHeadersWhenEmpty() {
        val headers = NtfyUtils.buildHeaders(
            setting = NtfySetting(
                title = "Custom",
                priority = "",
                tags = "  ",
                token = " ",
            ),
            msgInfo = MsgInfo(
                type = "sms",
                from = "10010",
                content = "test",
                date = Date(),
                simInfo = "SIM1",
            ),
        )

        assertEquals("Custom", headers["Title"])
        assertEquals("3", headers["Priority"])
        assertFalse(headers.containsKey("Tags"))
        assertFalse(headers.containsKey("Authorization"))
        assertEquals(2, headers.size)
    }
}
