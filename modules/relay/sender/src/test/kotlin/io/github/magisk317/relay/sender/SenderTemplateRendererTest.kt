package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.BatterySnapshot
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.NetworkSnapshot
import io.github.magisk317.relay.engine.model.SystemEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun renderTitle_substitutesReceiveTimeFromInstantDate() {
        val msgInfo = message(smsCode = "112233")

        // MsgInfo.date 已迁移为 kotlin.time.Instant；此处回归护栏确保
        // 默认 receiveTime 仍能正常格式化，而不是抛 IllegalArgumentException。
        val title = SenderTemplateRenderer.renderTitle("t={{RECEIVE_TIME}}", msgInfo)

        assertTrue(title.startsWith("t="), "unexpected prefix: $title")
        assertTrue(title.removePrefix("t=").isNotBlank(), "receive time not substituted: $title")
    }

    @Test
    fun renderTitle_cardSlotUsesRealSimSlotWhenAvailable() {
        assertEquals("卡槽 SIM1", SenderTemplateRenderer.renderTitle("卡槽 {{CARD_SLOT}}", message(simSlot = 0)))
        assertEquals("卡槽 SIM2", SenderTemplateRenderer.renderTitle("卡槽 {{CARD_SLOT}}", message(simSlot = 1)))
    }

    @Test
    fun renderTitle_cardSlotFallsBackToSmsSignatureAsIs() {
        // Issue #305 场景：短信无卡槽信息时，CARD_SLOT 回退到签名原样输出（需要【】由模板自己带）
        val msgInfo = message(simInfo = "京东银行", simSlot = -1)

        val title = SenderTemplateRenderer.renderTitle("短信_{{CARD_SLOT}}", msgInfo)

        assertEquals("短信_京东银行", title)
    }

    @Test
    fun renderTitle_cardSlotFallsBackToAppNameForAppNotify() {
        // 应用通知场景 simInfo 是应用名，原样输出
        val msgInfo = message(type = "app", simInfo = "微信", simSlot = -1)

        val title = SenderTemplateRenderer.renderTitle("{{CARD_SLOT}}", msgInfo)

        assertEquals("微信", title)
    }

    @Test
    fun renderTitle_rendersSmsSignatureAsIs() {
        val msgInfo = message(simInfo = "京东银行")

        val title = SenderTemplateRenderer.renderTitle("{{TITLE}}", msgInfo)

        assertEquals("京东银行", title)
    }

    @Test
    fun renderTitle_keepsBracketedSignatureAsIs() {
        val msgInfo = message(simInfo = "【京东银行】")

        val title = SenderTemplateRenderer.renderTitle("{{TITLE}}", msgInfo)

        assertEquals("【京东银行】", title)
    }

    @Test
    fun render_exposesMsgInfoOnlyVariables() {
        val msgInfo = message(subId = 3, callType = 3, contactName = "张三", phoneArea = "010")

        val rendered = SenderTemplateRenderer.render(
            "{{CARD_SUBID}}|{{CALL_TYPE}}|{{CONTACT_NAME}}|{{PHONE_AREA}}|[card_subid]|[contact_name]",
            msgInfo,
        )

        assertEquals("3|未接|张三|010|3|张三", rendered)
    }

    @Test
    fun render_rendersEnvVariablesWhenAttached() {
        val env = SystemEnvironment(
            deviceName = "mbp",
            appVersion = "1.0.7",
            battery = BatterySnapshot(percent = "80", status = "discharging", plugged = "no", fullInfo = "80% - discharging", simpleInfo = "80%"),
            network = NetworkSnapshot(netType = "wifi", ipv4 = "192.168.18.230", ipv6 = "::1", ipList = "192.168.18.230"),
            currentTime = 1_700_000_000_000L,
        )

        val rendered = SenderTemplateRenderer.render(
            "{{DEVICE_NAME}}|{{APP_VERSION}}|{{BATTERY_PCT}}|{{NET_TYPE}}|{{IPV4}}",
            message(systemEnv = env),
        )

        assertEquals("mbp|1.0.7|80|wifi|192.168.18.230", rendered)
    }

    @Test
    fun render_envVariablesAreBlankWhenSystemEnvMissing() {
        val rendered = SenderTemplateRenderer.render("{{DEVICE_NAME}}|{{BATTERY_PCT}}|{{CURRENT_TIME}}", message())

        assertEquals("||", rendered)
    }

    private fun message(
        smsCode: String = "654321",
        type: String = "sms",
        simInfo: String = "SIM1",
        simSlot: Int = -1,
        subId: Int = 0,
        callType: Int = 0,
        contactName: String = "",
        phoneArea: String = "",
        systemEnv: SystemEnvironment? = null,
    ): MsgInfo {
        return MsgInfo(
            type = type,
            from = "10690000",
            content = "Your code is $smsCode",
            date = Date(1_700_000_000_000L),
            simInfo = simInfo,
            simSlot = simSlot,
            subId = subId,
            callType = callType,
            contactName = contactName,
            phoneArea = phoneArea,
            smsCode = smsCode,
            systemEnv = systemEnv,
        )
    }
}
