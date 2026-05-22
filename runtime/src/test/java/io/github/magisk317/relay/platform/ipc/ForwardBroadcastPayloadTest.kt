package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.testing.stubLongExtra
import io.github.magisk317.relay.testing.stubSimRouting
import io.github.magisk317.relay.testing.stubStringExtra
import io.github.magisk317.relay.contract.constant.MessageType
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ForwardBroadcastPayloadTest {

    @Test
    fun fromIntent_readsContractExtras() {
        val intent = relaxedIntent()
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_SENDER, "Bank")
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_BODY, "Your code is 123456")
        intent.stubLongExtra(ForwardBroadcastContract.EXTRA_DATE, 123_456_789L)
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_COMPANY, "Bank App")
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_SMS_CODE, "123456")
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_PACKAGE_NAME, "com.bank.app")
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_NOTIFY_CHANNEL_ID, "main")
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_MSG_TYPE, ForwardBroadcastContract.MSG_TYPE_SMS)
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_FORWARD_SOURCE, ForwardBroadcastContract.SOURCE_SMS_HOOK)
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, "sms_test")
        every { intent.hasExtra(ForwardBroadcastContract.EXTRA_CALL_TYPE) } returns false
        intent.stubSimRouting(simSlot = 1, subId = 2)
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_CALL_STAGE, "")

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
        val source = relaxedIntent()
        source.stubSimRouting(
            simSlotKey = "slot",
            simSlot = 2,
            subIdKey = "subscription_id",
            subId = 9,
        )

        val payload = ForwardBroadcastPayload(eventId = "sms_test").withSimRoutingFrom(source)

        assertEquals(2, payload.simSlot)
        assertEquals(9, payload.subId)
        assertNull(ForwardBroadcastPayload(eventId = "sms_test").withSimRoutingFrom(null).simSlot)
    }
}
