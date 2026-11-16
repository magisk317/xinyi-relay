package io.github.magisk317.relay.ui.smscoderule

import io.github.magisk317.forward.engine.model.AppCodeRuleImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsCodeRuleEditorPolicyTest {
    @Test
    fun `blank keyword and regex are rejected with distinct errors`() {
        assertEquals(SmsCodeRuleInputError.EMPTY_KEYWORD, validateSmsCodeRuleInput("", "  ", "\\d+").error)
        assertEquals(SmsCodeRuleInputError.EMPTY_REGEX, validateSmsCodeRuleInput("", "code", "  ").error)
    }

    @Test
    fun `malformed regex is rejected before persistence`() {
        assertEquals(SmsCodeRuleInputError.INVALID_REGEX, validateSmsCodeRuleInput("", "code", "[").error)
    }

    @Test
    fun `normalization trims edges and preserves regex internal spaces`() {
        val result = validateSmsCodeRuleInput("  Bank  ", "  code  ", "  code (\\d{6})  ")
        assertEquals(null, result.error)
        assertEquals(SmsCodeRuleEditorInput("Bank", "code", "code (\\d{6})"), result.input)
        assertEquals(null, validateSmsCodeRuleInput("  ", "code", "\\d+").input?.company)
    }

    @Test
    fun `duplicate comparison normalizes blank company and excludes the edited id`() {
        val input = validateSmsCodeRuleInput("", " code ", " \\d+ ").input!!
        val existing = listOf(AppCodeRuleImpl(company = " ", codeKeyword = " code ", codeRegex = " \\d+ ", id = 7L))
        assertTrue(isDuplicateSmsCodeRule(input, ruleId = 0L, existing))
        assertFalse(isDuplicateSmsCodeRule(input, ruleId = 7L, existing))
    }

    @Test
    fun `different company keyword or regex is not a duplicate`() {
        val input = SmsCodeRuleEditorInput("Bank", "code", "\\d+")
        val existing = listOf(
            AppCodeRuleImpl(company = "Shop", codeKeyword = "code", codeRegex = "\\d+", id = 1L),
            AppCodeRuleImpl(company = "Bank", codeKeyword = "otp", codeRegex = "\\d+", id = 2L),
            AppCodeRuleImpl(company = "Bank", codeKeyword = "code", codeRegex = "\\d{6}", id = 3L),
        )
        assertFalse(isDuplicateSmsCodeRule(input, ruleId = 0L, existing))
    }
}
