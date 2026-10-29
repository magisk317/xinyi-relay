package io.github.magisk317.relay.xp.notification

import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsCodeNotificationAnalyzerTest {

    @Test
    fun `does not treat domain token as verification code for telephony sms notifications`() {
        val content =
            "【平安银行】您是我行优质客户，阅读业务条款后回复260404确认办理，可申请取现金额50000.00元，" +
                "快至实时划转到储蓄卡1822。日利率0.050%，年化利率18.25%（单利），且手续费一次性收取0.000%。" +
                "请务必点击 br.pingan.com/zYR 阅读业务条款，回复短信即视为您同意办理，04月07日前有效，以审批为准。拒收请回复R"

        assertEquals("", SmsCodeNotificationAnalyzer.extractCode(content, source = null))
    }

    @Test
    fun `extracts numeric verification code when real keyword is present`() {
        val content = "【平安银行】您的验证码123456，请勿泄露给他人。"

        assertEquals("123456", SmsCodeNotificationAnalyzer.extractCode(content, source = null))
    }

    @Test
    fun `extracts alphanumeric code for standalone english keyword`() {
        val content = "Verification code: AB12, valid for 5 minutes."

        assertEquals("AB12", SmsCodeNotificationAnalyzer.extractCode(content, source = null))
    }

    @Test
    fun `extracts gmail notification code with package source`() {
        val content = "Security\nYour verification code is G7H9"

        assertEquals(
            "G7H9",
            SmsCodeNotificationAnalyzer.extractCode(
                content = content,
                source = SmsCodeParseSource(
                    packageName = "com.google.android.gm",
                    sender = "Security",
                    title = "Security",
                    sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
                ),
            ),
        )
    }

    @Test
    fun `rejects package-scoped notification rule when package source mismatches`() {
        val content = "Google Voice\nYour Google Voice verification code is GV42"

        assertEquals(
            "",
            SmsCodeNotificationAnalyzer.extractCode(
                content = content,
                source = SmsCodeParseSource(
                    packageName = "com.example.chat",
                    sender = "Google Voice",
                    title = "Google Voice",
                    sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
                ),
            ),
        )
    }

    @Test
    fun `does not treat amount date or order numbers as notification verification code`() {
        val source = SmsCodeParseSource(
            packageName = "com.example.shop",
            sender = "Shop",
            title = "Shop",
            sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
        )
        listOf(
            "Verification amount is 50000.00 USD.",
            "Verification date is 2026年07月06日.",
            "Order 246810 has shipped and tracking details are ready. Verification code will arrive separately.",
        ).forEach { content ->
            assertEquals("", SmsCodeNotificationAnalyzer.extractCode(content, source), "content=$content")
        }
    }

    @Test
    fun `detects telephony sms packages from the allowlist and keyword`() {
        assertTrue(SmsCodeNotificationAnalyzer.isTelephonySmsPackage("com.android.phone"))
        assertTrue(SmsCodeNotificationAnalyzer.isTelephonySmsPackage("com.example.telephony.data"))
        assertFalse(SmsCodeNotificationAnalyzer.isTelephonySmsPackage("com.tencent.mm"))
    }

    @Test
    fun `company label prefers bracket sender over fallback`() {
        val label = SmsCodeNotificationAnalyzer.resolveCompanyLabel(
            title = "【平安银行】",
            body = "您的验证码123456",
            expandedText = "",
            tickerText = "",
            fallbackLabel = "Fallback",
        )

        assertEquals("平安银行", label)
    }

    @Test
    fun `company label falls back to title then fallback label`() {
        assertEquals(
            "Bank",
            SmsCodeNotificationAnalyzer.resolveCompanyLabel("Bank", "body", "", "", "Fallback"),
        )
        assertEquals(
            "Fallback",
            SmsCodeNotificationAnalyzer.resolveCompanyLabel("", "body", "", "", "Fallback"),
        )
    }
}
