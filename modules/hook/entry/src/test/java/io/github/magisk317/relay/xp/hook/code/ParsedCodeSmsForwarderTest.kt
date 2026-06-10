package io.github.magisk317.relay.xp.hook.code

import android.content.Intent
import io.github.magisk317.relay.testing.clearXpLogSink
import io.github.magisk317.relay.testing.hookSmsMsg
import io.github.magisk317.relay.testing.installSilentXpLogSink
import io.github.magisk317.relay.testing.relaxedHookContexts
import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.xpbridge.PreparedSmsHookDispatch
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParsedCodeSmsForwarderTest {

    @AfterEach
    fun tearDown() {
        clearXpLogSink()
    }

    @Test
    fun forwardIfCodeSms_dispatchesAndMarksIntent() {
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        val smsMsg = hookSmsMsg(body = "otp 123456")
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
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        val smsMsg = hookSmsMsg(body = "plain body")
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
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        val smsMsg = hookSmsMsg(body = "otp 654321")
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
}
