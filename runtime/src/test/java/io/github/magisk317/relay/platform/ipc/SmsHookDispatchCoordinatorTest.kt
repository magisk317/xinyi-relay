package io.github.magisk317.relay.platform.ipc

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsHookDispatchCoordinatorTest {

    @Test
    fun prepareParsedSms_usesPayloadFactoryResult() {
        val smsMsg = SmsMsg(sender = "1068", body = "code 123456", smsCode = "123456")
        val sourceIntent = Intent("test")

        val prepared = SmsHookDispatchCoordinator.prepareParsedSms(
            smsMsg = smsMsg,
            sourceIntent = sourceIntent,
            eventId = "sms_evt",
        ) { msg, eventId, intent ->
            assertEquals(smsMsg, msg)
            assertEquals("sms_evt", eventId)
            assertEquals(sourceIntent, intent)
            ForwardBroadcastPayload(
                sender = msg.sender,
                body = msg.body,
                smsCode = msg.smsCode,
                eventId = eventId.orEmpty(),
            )
        }

        assertEquals(smsMsg, prepared.smsMsg)
        assertEquals("sms_evt", prepared.payload.eventId)
        assertEquals("123456", prepared.payload.smsCode)
        assertEquals(null, prepared.messageType)
    }

    @Test
    fun prepareIngressSms_mapsIngressResultIntoPreparedDispatch() = runBlocking {
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val smsMsg = SmsMsg(sender = "1068", body = "code 123456")
        val enriched = smsMsg.copy(smsCode = "123456", packageName = "com.bank.app")

        val prepared = SmsHookDispatchCoordinator.prepareIngressSms(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            sourceIntent = null,
            eventId = "sms_evt",
        ) { _, _, _, _, eventId ->
            SmsIngressAdapter.Result(
                smsMsg = enriched,
                payload = ForwardBroadcastPayload(
                    sender = enriched.sender,
                    body = enriched.body,
                    smsCode = enriched.smsCode,
                    packageName = enriched.packageName,
                    eventId = eventId.orEmpty(),
                ),
                messageType = MessageType.SMS_CODE,
            )
        }

        assertNotNull(prepared)
        assertEquals(enriched, prepared!!.smsMsg)
        assertEquals(MessageType.SMS_CODE, prepared.messageType)
        assertEquals("com.bank.app", prepared.payload.packageName)
    }

    @Test
    fun dispatchPreparedSms_delegatesToSmsHookDispatcher() {
        val context = mockk<Context>(relaxed = true)
        val prepared = PreparedSmsHookDispatch(
            smsMsg = SmsMsg(sender = "1068", body = "code 123456", smsCode = "123456"),
            payload = ForwardBroadcastPayload(eventId = "sms_evt"),
        )
        var dispatchedToken: String? = null

        val result = SmsHookDispatchCoordinator.dispatchPreparedSms(
            context = context,
            prepared = prepared,
            sentFromUid = 20000,
            sdkInt = 34,
            tokenResolver = { "token123" },
            dispatchBlock = { token -> dispatchedToken = token },
        )

        assertTrue(result.dispatched)
        assertTrue(result.tokenPresent)
        assertFalse(result.bypassUsed)
        assertEquals("token123", dispatchedToken)
    }
}
