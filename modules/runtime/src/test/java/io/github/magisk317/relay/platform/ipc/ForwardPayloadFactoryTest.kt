package io.github.magisk317.relay.platform.ipc

import android.os.Bundle
import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.testing.runtimeSmsMsg
import io.github.magisk317.relay.testing.stubByteArrayExtra
import io.github.magisk317.relay.testing.stubSimRouting
import io.github.magisk317.relay.testing.stubStringExtra
import io.mockk.every
import io.mockk.mockk
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
    fun ensureSmsEventId_usesStableSmsFieldsForDifferentIntentInstances() {
        val firstIntent = relaxedIntent()
        val secondIntent = relaxedIntent()
        firstIntent.stubStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, null)
        secondIntent.stubStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, null)
        every { firstIntent.extras } returns null
        every { secondIntent.extras } returns null
        val smsMsg = runtimeSmsMsg(sender = "Bank", body = "code 123456", date = 100L)

        val firstEventId = ForwardPayloadFactory.ensureSmsEventId(firstIntent, smsMsg)
        val secondEventId = ForwardPayloadFactory.ensureSmsEventId(secondIntent, smsMsg)

        assertEquals(firstEventId, secondEventId)
        assertTrue(firstEventId.startsWith("sms_"))
    }

    @Test
    fun ensureSmsEventId_withSmsMsgPrefersPduHashToMatchHookResolver() {
        val rawPdu = arrayOf(byteArrayOf(0x01, 0x02, 0x03))
        val hookIntent = relaxedIntentWithPdus(rawPdu)
        val standardIntent = relaxedIntentWithPdus(rawPdu)

        val hookEventId = ForwardPayloadFactory.ensureSmsEventId(hookIntent)
        val standardEventId = ForwardPayloadFactory.ensureSmsEventId(
            standardIntent,
            runtimeSmsMsg(sender = "Bank", body = "code 123456", date = 100L),
        )

        assertEquals(hookEventId, standardEventId)
    }

    @Test
    fun smsPayload_usesStableEventIdWhenEventIdIsMissing() {
        val smsMsg = runtimeSmsMsg(sender = "Bank", body = "code 123456", date = 100L)

        val firstPayload = ForwardPayloadFactory.smsPayload(smsMsg)
        val secondPayload = ForwardPayloadFactory.smsPayload(smsMsg)

        assertEquals(firstPayload.eventId, secondPayload.eventId)
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
    fun mmsPayload_preservesMetadataAndSimRouting() {
        val sourceIntent = relaxedIntent()
        sourceIntent.stubStringExtra("from", "10086")
        sourceIntent.stubStringExtra("subject", "Picture")
        sourceIntent.stubStringExtra("content_location", "http://mms.example/message")
        sourceIntent.stubStringExtra("transaction_id", "tx-1")
        sourceIntent.stubSimRouting(
            simSlotKey = ForwardBroadcastContract.EXTRA_SIM_SLOT,
            simSlot = 2,
            subIdKey = ForwardBroadcastContract.EXTRA_SUB_ID,
            subId = 8,
        )

        val payload = ForwardPayloadFactory.mmsPayload(sourceIntent, receivedAt = 123L)

        assertEquals("10086", payload.sender)
        assertEquals("Picture\nhttp://mms.example/message\ntx-1", payload.body)
        assertEquals(123L, payload.date)
        assertEquals(1, payload.simSlot)
        assertEquals(8, payload.subId)
        assertEquals(ForwardBroadcastContract.MSG_TYPE_SMS, payload.msgType)
        assertEquals(ForwardBroadcastContract.SOURCE_SMS_HOOK, payload.forwardSource)
        assertTrue(payload.eventId.startsWith("mms_"))
    }

    @Test
    fun mmsPayload_usesRawPduHashWhenMetadataIsMissing() {
        val sourceIntent = relaxedIntent()
        sourceIntent.stubByteArrayExtra("data", byteArrayOf(0x8c.toByte(), 0x82.toByte(), 0x98.toByte()))

        val payload = ForwardPayloadFactory.mmsPayload(sourceIntent, receivedAt = 456L)

        assertEquals("MMS", payload.sender)
        assertEquals("MMS received", payload.body)
        assertEquals(456L, payload.date)
        assertTrue(payload.eventId.startsWith("mms_"))
    }

    @Test
    fun mmsPayload_usesStableEventIdForSameRawPdu() {
        val firstIntent = relaxedIntent()
        val secondIntent = relaxedIntent()
        val rawPdu = byteArrayOf(0x8c.toByte(), 0x82.toByte(), 0x98.toByte(), 0x01)
        firstIntent.stubByteArrayExtra("data", rawPdu)
        secondIntent.stubByteArrayExtra("data", rawPdu)

        val firstPayload = ForwardPayloadFactory.mmsPayload(firstIntent, receivedAt = 100L)
        val secondPayload = ForwardPayloadFactory.mmsPayload(secondIntent, receivedAt = 200L)

        assertEquals(firstPayload.eventId, secondPayload.eventId)
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
            smsCode = "123456",
        )
        assertEquals(ForwardBroadcastContract.SOURCE_NOTIFICATION_LISTENER, notifyPayload.forwardSource)
        assertEquals(ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY, notifyPayload.msgType)
        assertEquals("123456", notifyPayload.smsCode)
        assertEquals(io.github.magisk317.relay.contract.constant.MessageType.APP_NOTIFY, notifyPayload.resolveRelayMessageType())
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

    private fun relaxedIntentWithPdus(pdus: Array<ByteArray>) = relaxedIntent().also { intent ->
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, null)
        val extras = mockk<Bundle>()
        every { extras.get("pdus") } returns pdus
        every { intent.extras } returns extras
    }
}
