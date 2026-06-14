package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Date

class SenderTemplateRendererTest {
    @Test
    fun renderTitle_replacesSmsCodeVariable() {
        val msgInfo = message(smsCode = "654321")

        val title = SenderTemplateRenderer.renderTitle("验证码 {{SMS_CODE}}", msgInfo)

        assertEquals("验证码 654321", title)
    }

    @Test
    fun render_replacesLegacyCodePlaceholders() {
        val msgInfo = message(smsCode = "246810")

        val rendered = SenderTemplateRenderer.render("[code] {{CODE}} [sms_code] {{SMS_CODE}}", msgInfo)

        assertEquals("246810 246810 246810 246810", rendered)
    }

    private fun message(smsCode: String): MsgInfo {
        return MsgInfo(
            from = "10690000",
            content = "Your code is $smsCode",
            date = Date(1_700_000_000_000L),
            simInfo = "SIM1",
            smsCode = smsCode,
        )
    }
}
