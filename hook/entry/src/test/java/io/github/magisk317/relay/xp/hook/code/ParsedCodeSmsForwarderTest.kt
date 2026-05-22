package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.mockk.mockk
import io.github.magisk317.relay.xpbridge.PreparedSmsHookDispatch
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.smscode.xposed.utils.XLog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParsedCodeSmsForwarderTest {

    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
    }

    @Test
    fun forwardIfCodeSms_dispatchesAndMarksIntent() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        val smsMsg = smsMsg(body = "otp 123456")
        var preparedEventId: String? = null
        var preparedIntent: Intent? = null
        var dispatchedPrepared: PreparedSmsHookDispatch? = null
        var dispatchedEventId: String? = null
        var markedIntent: Intent? = null
        val forwarder = ParsedCodeSmsForwarder(
            smsForwardPreparer = { _, _, _, sourceIntent, eventId ->
                preparedIntent = sourceIntent
                preparedEventId = eventId
                XpDispatchCoordinator.prepareParsedSms(
                    smsMsg = smsMsg.copy(smsCode = "123456"),
                    sourceIntent = sourceIntent,
                    eventId = eventId,
                )
            },
            smsForwardDispatcher = { _, prepared, eventId ->
                dispatchedPrepared = prepared
                dispatchedEventId = eventId
                true
            },
            parsedForwardMarker = { markedIntent = it },
        )

        val forwarded = forwarder.forwardIfCodeSms(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            sourceIntent = intent,
            eventId = "evt-code",
        )

        assertTrue(forwarded)
        assertSame(intent, preparedIntent)
        assertEquals("evt-code", preparedEventId)
        assertEquals("123456", dispatchedPrepared?.smsMsg?.smsCode)
        assertEquals("evt-code", dispatchedEventId)
        assertSame(intent, markedIntent)
    }

    @Test
    fun forwardIfCodeSms_skipsWhenPreparedSmsHasNoCode() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        val smsMsg = smsMsg(body = "plain body")
        var dispatched = false
        var marked = false
        val forwarder = ParsedCodeSmsForwarder(
            smsForwardPreparer = { _, _, _, sourceIntent, eventId ->
                XpDispatchCoordinator.prepareParsedSms(
                    smsMsg = smsMsg,
                    sourceIntent = sourceIntent,
                    eventId = eventId,
                )
            },
            smsForwardDispatcher = { _, _, _ ->
                dispatched = true
                true
            },
            parsedForwardMarker = { marked = true },
        )

        val forwarded = forwarder.forwardIfCodeSms(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            sourceIntent = intent,
            eventId = "evt-plain",
        )

        assertFalse(forwarded)
        assertFalse(dispatched)
        assertFalse(marked)
    }

    @Test
    fun forwardIfCodeSms_doesNotMarkIntentWhenDispatchFails() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        val smsMsg = smsMsg(body = "otp 654321")
        var marked = false
        val forwarder = ParsedCodeSmsForwarder(
            smsForwardPreparer = { _, _, _, sourceIntent, eventId ->
                XpDispatchCoordinator.prepareParsedSms(
                    smsMsg = smsMsg.copy(smsCode = "654321"),
                    sourceIntent = sourceIntent,
                    eventId = eventId,
                )
            },
            smsForwardDispatcher = { _, _, _ -> false },
            parsedForwardMarker = { marked = true },
        )

        val forwarded = forwarder.forwardIfCodeSms(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            sourceIntent = intent,
            eventId = "evt-fail",
        )

        assertFalse(forwarded)
        assertFalse(marked)
    }

    private fun smsMsg(body: String): SmsMsg {
        return SmsMsg(
            sender = "1068",
            body = body,
            date = 100L,
            msgType = SmsMsg.MSG_TYPE_SMS,
        )
    }

    private fun stubXLog() {
        XLog.setTestSink { _, _ -> }
    }
}
