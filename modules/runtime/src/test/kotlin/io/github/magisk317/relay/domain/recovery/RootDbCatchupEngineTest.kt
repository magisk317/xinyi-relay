package io.github.magisk317.relay.domain.recovery

import io.github.magisk317.relay.contract.constant.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootDbCatchupEngineTest {

    @Test
    fun callTypeLabel_mapsKnownTypes() {
        assertEquals("来电", RootDbCatchupEngine.callTypeLabel(1))
        assertEquals("去电", RootDbCatchupEngine.callTypeLabel(2))
        assertEquals("未接", RootDbCatchupEngine.callTypeLabel(3))
        assertEquals("语音信箱", RootDbCatchupEngine.callTypeLabel(4))
        assertEquals("拒接", RootDbCatchupEngine.callTypeLabel(5))
        assertEquals("拦截", RootDbCatchupEngine.callTypeLabel(6))
        assertEquals("异地接听", RootDbCatchupEngine.callTypeLabel(7))
    }

    @Test
    fun callTypeLabel_mapsUnknownTypes() {
        assertEquals("未知类型(0)", RootDbCatchupEngine.callTypeLabel(0))
        assertEquals("未知类型(99)", RootDbCatchupEngine.callTypeLabel(99))
    }

    @Test
    fun buildSmsRelayEvent_mapsMessageType() {
        val row = RootDbCatchupEngine.SmsRow(
            id = 1L,
            address = "10690001",
            body = "验证码 123456",
            date = 1_700_000_000_000L,
        )

        val codeEvent = RootDbCatchupEngine.buildSmsRelayEvent(row, MessageType.SMS_CODE, "123456")
        assertEquals(MessageType.SMS_CODE, codeEvent.messageType)
        assertEquals("root_db_catchup_sms", codeEvent.sourceType)
        assertEquals("10690001", codeEvent.sender)
        assertEquals("验证码 123456", codeEvent.body)
        assertEquals(1_700_000_000_000L, codeEvent.timestamp)
        assertEquals("", codeEvent.packageName)
        assertEquals("", codeEvent.notifyChannelId)
        assertEquals("", codeEvent.companyOrAppName)
        assertEquals("123456", codeEvent.smsCode)
        assertEquals(0, codeEvent.callType)
    }

    @Test
    fun buildCallRelayEvent_usesCallLabelAndNumber() {
        val row = RootDbCatchupEngine.CallRow(
            id = 7L,
            number = "10086",
            date = 1_700_000_123_000L,
            callType = 3,
        )

        val event = RootDbCatchupEngine.buildCallRelayEvent(row)
        assertEquals(MessageType.CALL_NOTIFY, event.messageType)
        assertEquals("root_db_catchup_call", event.sourceType)
        assertEquals("10086", event.sender)
        assertEquals("通话通知：未接\n号码：10086", event.body)
        assertEquals(1_700_000_123_000L, event.timestamp)
        assertEquals("未接", event.companyOrAppName)
        assertEquals(3, event.callType)
    }
}
