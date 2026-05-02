package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.engine.event.RelayEvent
import io.github.magisk317.relay.engine.model.BatterySnapshot
import io.github.magisk317.relay.engine.model.NetworkSnapshot
import io.github.magisk317.relay.engine.model.SystemEnvironment
import io.github.magisk317.relay.contract.model.ForwardCommonConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageFormatterTest {

    private fun formatter(simSlotRemarkResolver: (Int) -> String = { "" }) = MessageFormatter(
        systemInfoProvider = object : SystemInfoProvider {
            override fun getSnapshot(deviceName: String): SystemEnvironment = snapshot

            override fun resolveAppName(packageName: String): String = "ResolvedApp"
        },
        simSlotRemarkResolver = simSlotRemarkResolver,
    )

    private val snapshot = SystemEnvironment(
        battery = BatterySnapshot(
            percent = "80%",
            status = "充电中",
            plugged = "USB",
            fullInfo = "80%, charging",
            simpleInfo = "80%",
        ),
        network = NetworkSnapshot(
            ipv4 = "127.0.0.1",
            ipv6 = "::1",
            ipList = "127.0.0.1,::1",
            netType = "Wi-Fi",
        ),
        currentTime = 1_700_000_000_000L,
        deviceName = "RelayDevice",
        appVersion = "1.0.0",
    )

    @Test
    fun `app notify formatting uses app wording`() {
        val result = formatter().format(
            event = baseEvent.copy(
                messageType = MessageType.APP_NOTIFY,
                sender = "微信支付",
                body = "到账 52 元",
                companyOrAppName = "微信",
                simSlot = -1,
            ),
            payloadContext = DispatchPayloadContext(
                appName = "微信",
                title = "微信支付",
                message = "到账 52 元",
            ),
            config = ForwardCommonConfig(messageTemplate = "卡槽：{{CARD_SLOT}}\n【卡槽与来源】"),
            env = snapshot,
        )

        assertTrue(result.contains("应用：微信"))
        assertTrue(result.contains("【应用与来源】"))
    }

    @Test
    fun `call notify formatting uses call wording`() {
        val result = formatter().format(
            event = baseEvent.copy(
                messageType = MessageType.CALL_NOTIFY,
                callType = 3,
            ),
            payloadContext = DispatchPayloadContext.from(baseEvent.copy(messageType = MessageType.CALL_NOTIFY, callType = 3)),
            config = ForwardCommonConfig(messageTemplate = "卡槽：{{CARD_SLOT}}\n【卡槽与来源】"),
            env = snapshot,
        )

        assertTrue(result.contains("通话：SIM1"))
        assertTrue(result.contains("【通话与来源】"))
    }

    @Test
    fun `sms formatting keeps card slot wording`() {
        val result = formatter().format(
            event = baseEvent.copy(messageType = MessageType.SMS_PLAIN),
            payloadContext = DispatchPayloadContext.from(baseEvent.copy(messageType = MessageType.SMS_PLAIN)),
            config = ForwardCommonConfig(messageTemplate = "卡槽：{{CARD_SLOT}}"),
            env = snapshot,
        )

        assertTrue(result.contains("卡槽：SIM1"))
        assertFalse(result.contains("应用："))
        assertFalse(result.contains("通话："))
    }

    @Test
    fun `sms formatting prefers configured sim remark`() {
        val result = formatter { simSlot ->
            if (simSlot == 0) "联通主卡" else ""
        }.format(
            event = baseEvent.copy(messageType = MessageType.SMS_PLAIN),
            payloadContext = DispatchPayloadContext.from(baseEvent.copy(messageType = MessageType.SMS_PLAIN)),
            config = ForwardCommonConfig(messageTemplate = "卡槽：{{CARD_SLOT}}"),
            env = snapshot,
        )

        assertTrue(result.contains("卡槽：联通主卡"))
        assertFalse(result.contains("卡槽：SIM1"))
    }

    private companion object {
        val baseEvent = RelayEvent(
            messageType = MessageType.SMS_CODE,
            sourceType = "test",
            sender = "10690001234",
            body = "验证码 123456",
            timestamp = 1_700_000_000_000L,
            packageName = "com.example.app",
            notifyChannelId = "",
            companyOrAppName = "SIM1",
            smsCode = "123456",
            callType = 0,
            callStage = "",
            simSlot = 0,
            subId = 1,
        )
    }
}
