package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.testing.runtimeSmsMsg
import io.github.magisk317.relay.testing.stubSimRouting
import io.github.magisk317.relay.testing.stubStringExtra
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForwardPayloadFactoryTest {

    @Test
    fun ensureSmsEventId_reusesExistingValue() {
        val intent = relaxedIntent()
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, "sms_existing")

        val eventId = ForwardPayloadFactory.ensureSmsEventId(intent)

        assertEquals("sms_existing", eventId)
    }

    @Test
    fun smsPayload_usesSmsMsgAndSimRouting() {
        val sourceIntent = relaxedIntent()
        sourceIntent.stubSimRouting(
            simSlotKey = "slot",
            simSlot = 1,
            subIdKey = "subscription_id",
            subId = 7,
        )

        val payload = ForwardPayloadFactory.smsPayload(
            smsMsg = runtimeSmsMsg(
                sender = "Bank",
                body = "code 123456",
                date = 1L,
                company = "Bank App",
                smsCode = "123456",
                packageName = "com.bank.app",
            ),
            eventId = "sms_test",
            sourceIntent = sourceIntent,
        )

        assertEquals("sms_test", payload.eventId)
        assertEquals(1, payload.simSlot)
        assertEquals(7, payload.subId)
        assertEquals(ForwardBroadcastContract.SOURCE_SMS_HOOK, payload.forwardSource)
    }

    @Test
    fun appNotificationPayload_and_callPayload_useExpectedSources() {
        val notifyPayload = ForwardPayloadFactory.appNotificationPayload(
            packageName = "com.chat.app",
            title = "Alice",
            body = "hello",
            timestamp = 100L,
            appName = "Chat",
            notifyChannelId = "main",
        )
        assertEquals(ForwardBroadcastContract.SOURCE_NOTIFICATION_LISTENER, notifyPayload.forwardSource)
        assertTrue(notifyPayload.eventId.startsWith("nls_"))

        val callPayload = ForwardPayloadFactory.callPayload(
            packageName = "io.github.magisk317.xinyi.relay",
            sender = "10086",
            body = "incoming",
            company = "Phone",
            timestamp = 200L,
            callType = 1,
            callStage = "ringing",
        )
        assertEquals(ForwardBroadcastContract.SOURCE_TELEPHONY_STATE, callPayload.forwardSource)
        assertTrue(callPayload.eventId.startsWith("tel_"))
        assertEquals("ringing", callPayload.callStage)
    }
}
