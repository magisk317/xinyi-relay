package io.github.magisk317.relay.feature.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KeywordAlertMatcherTest {
    @Test
    fun `parseKeywords splits common separators`() {
        assertEquals(
            listOf("违停", "验证码", "外卖"),
            KeywordAlertMatcher.parseKeywords("违停, 验证码\n外卖；违停"),
        )
    }

    @Test
    fun `firstMatchedKeyword matches ignore case`() {
        assertEquals(
            "otp",
            KeywordAlertMatcher.firstMatchedKeyword("otp\n违停", "Your OTP is 123456"),
        )
    }

    @Test
    fun `firstMatchedKeyword returns null when unmatched`() {
        assertNull(
            KeywordAlertMatcher.firstMatchedKeyword("违停\n快递", "银行验证码 123456"),
        )
    }
}
