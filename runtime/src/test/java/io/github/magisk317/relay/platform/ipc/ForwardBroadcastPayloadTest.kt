package io.github.magisk317.relay.platform.ipc

import android.content.Intent
import io.github.magisk317.relay.common.constant.MessageType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ForwardBroadcastPayloadTest {

    @Test
    fun fromIntent_readsContractExtras() {
        val intent = mockk<Intent>()
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_SENDER) } returns "Bank"
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_BODY) } returns "Your code is 123456"
        every { intent.getLongExtra(ForwardBroadcastContract.EXTRA_DATE, 0L) } returns 123_456_789L
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_COMPANY) } returns "Bank App"
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_SMS_CODE) } returns "123456"
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_PACKAGE_NAME) } returns "com.bank.app"
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_NOTIFY_CHANNEL_ID) } returns "main"
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_MSG_TYPE) } returns ForwardBroadcastContract.MSG_TYPE_SMS
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_FORWARD_SOURCE) } returns ForwardBroadcastContract.SOURCE_SMS_HOOK
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID) } returns "sms_test"
        every { intent.hasExtra(ForwardBroadcastContract.EXTRA_CALL_TYPE) } returns false
        every { intent.hasExtra(ForwardBroadcastContract.EXTRA_SIM_SLOT) } returns true
        every { intent.hasExtra("slot") } returns false
        every { intent.hasExtra("simId") } returns false
        every { intent.hasExtra("sim_id") } returns false
        every { intent.hasExtra("simSlot") } returns false
        every { intent.hasExtra("android.telephony.extra.SLOT_INDEX") } returns false
        every { intent.getIntExtra(ForwardBroadcastContract.EXTRA_SIM_SLOT, Int.MIN_VALUE) } returns 1
        every { intent.hasExtra(ForwardBroadcastContract.EXTRA_SUB_ID) } returns true
        every { intent.hasExtra("subscription") } returns false
        every { intent.hasExtra("subscription_id") } returns false
        every { intent.hasExtra("android.telephony.extra.SUBSCRIPTION_INDEX") } returns false
        every { intent.hasExtra("android.telephony.extra.SUBSCRIPTION_ID") } returns false
        every { intent.getIntExtra(ForwardBroadcastContract.EXTRA_SUB_ID, Int.MIN_VALUE) } returns 2
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_CALL_STAGE) } returns ""

        val restored = ForwardBroadcastPayload.fromIntent(intent)

        assertEquals(
            ForwardBroadcastPayload(
                sender = "Bank",
                body = "Your code is 123456",
                date = 123_456_789L,
                company = "Bank App",
                smsCode = "123456",
                packageName = "com.bank.app",
                notifyChannelId = "main",
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
                eventId = "sms_test",
                simSlot = 1,
                subId = 2,
            ),
            restored,
        )
    }

    @Test
    fun toRelayEvent_mapsSmsAndNotifyTypes() {
        val smsPayload = ForwardBroadcastPayload(
            sender = "Bank",
            body = "123456",
            date = 1L,
            company = "Bank App",
            smsCode = "123456",
            packageName = "com.bank.app",
            msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
            forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
            eventId = "sms_test",
        )
        assertEquals(MessageType.SMS_CODE, smsPayload.toRelayEvent().messageType)

        val notifyPayload = ForwardBroadcastPayload(
            sender = "Dialer",
            body = "Incoming call",
            date = 2L,
            company = "Phone",
            packageName = "com.android.dialer",
            notifyChannelId = "phone",
            msgType = ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY,
            forwardSource = ForwardBroadcastContract.SOURCE_TELEPHONY_STATE,
            eventId = "call_test",
            callType = 1,
            callStage = "ringing",
        )
        val relayEvent = notifyPayload.toRelayEvent(contactName = "Alice", phoneArea = "Shanghai")
        assertEquals(MessageType.CALL_NOTIFY, relayEvent.messageType)
        assertEquals("Alice", relayEvent.contactName)
        assertEquals("Shanghai", relayEvent.phoneArea)
        assertEquals("ringing", relayEvent.callStage)
    }

    @Test
    fun withSimRoutingFrom_readsLegacyAliases() {
        val source = mockk<Intent>()
        every { source.hasExtra("slot") } returns true
        every { source.getIntExtra("slot", Int.MIN_VALUE) } returns 2
        every { source.hasExtra("subscription") } returns false
        every { source.hasExtra("subscription_id") } returns true
        every { source.getIntExtra("subscription_id", Int.MIN_VALUE) } returns 9
        every { source.hasExtra(ForwardBroadcastContract.EXTRA_SUB_ID) } returns false
        every { source.hasExtra(ForwardBroadcastContract.EXTRA_SIM_SLOT) } returns false
        every { source.hasExtra("simId") } returns false
        every { source.hasExtra("sim_id") } returns false
        every { source.hasExtra("simSlot") } returns false
        every { source.hasExtra("android.telephony.extra.SLOT_INDEX") } returns false
        every { source.hasExtra("android.telephony.extra.SUBSCRIPTION_INDEX") } returns false
        every { source.hasExtra("android.telephony.extra.SUBSCRIPTION_ID") } returns false

        val payload = ForwardBroadcastPayload(eventId = "sms_test").withSimRoutingFrom(source)

        assertEquals(2, payload.simSlot)
        assertEquals(9, payload.subId)
        assertNull(ForwardBroadcastPayload(eventId = "sms_test").withSimRoutingFrom(null).simSlot)
    }
}
