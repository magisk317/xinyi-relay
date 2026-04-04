package io.github.magisk317.relay.platform.ipc

import android.content.Intent
import dev.mokkery.MockMode.autofill
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.answering.returns
import io.github.magisk317.relay.data.db.entity.SmsMsg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForwardPayloadFactoryTest {

    @Test
    fun ensureSmsEventId_reusesExistingValue() {
        val intent = mock<Intent>(autofill)
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID) } returns "sms_existing"

        val eventId = ForwardPayloadFactory.ensureSmsEventId(intent)

        assertEquals("sms_existing", eventId)
    }

    @Test
    fun smsPayload_usesSmsMsgAndSimRouting() {
        val sourceIntent = mock<Intent>(autofill)
        every { sourceIntent.hasExtra("slot") } returns true
        every { sourceIntent.getIntExtra("slot", Int.MIN_VALUE) } returns 1
        every { sourceIntent.hasExtra("subscription") } returns false
        every { sourceIntent.hasExtra("subscription_id") } returns true
        every { sourceIntent.getIntExtra("subscription_id", Int.MIN_VALUE) } returns 7
        every { sourceIntent.hasExtra(ForwardBroadcastContract.EXTRA_SUB_ID) } returns false
        every { sourceIntent.hasExtra(ForwardBroadcastContract.EXTRA_SIM_SLOT) } returns false
        every { sourceIntent.hasExtra("simId") } returns false
        every { sourceIntent.hasExtra("sim_id") } returns false
        every { sourceIntent.hasExtra("simSlot") } returns false
        every { sourceIntent.hasExtra("android.telephony.extra.SLOT_INDEX") } returns false
        every { sourceIntent.hasExtra("android.telephony.extra.SUBSCRIPTION_INDEX") } returns false
        every { sourceIntent.hasExtra("android.telephony.extra.SUBSCRIPTION_ID") } returns false

        val payload = ForwardPayloadFactory.smsPayload(
            smsMsg = SmsMsg(
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
