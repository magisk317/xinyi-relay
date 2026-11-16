package io.github.magisk317.relay.xp.hook.code

import android.content.Intent
import io.mockk.every
import io.github.magisk317.relay.testing.clearXpLogSink
import io.github.magisk317.relay.testing.hookSmsMsg
import io.github.magisk317.relay.testing.installSilentXpLogSink
import io.github.magisk317.relay.testing.relaxedHookContexts
import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.xpbridge.PreparedSmsHookDispatch
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    fun forwardIfCodeSms_doesNotRepeatAnAlreadyAcceptedBroadcast() {
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        every { intent.getBooleanExtra(any(), false) } returns true
        var prepared = false
        val forwarder = ParsedCodeSmsForwarder(
            smsForwardPreparer = { _, _, _, _, _ -> prepared = true; null },
        )

        assertTrue(forwarder.forwardIfCodeSms(
            pluginContext, phoneContext, hookSmsMsg(body = "code 123456"), intent, "event",
        ))
        assertFalse(prepared)
    }

    @Test
    fun concurrentInstancesCannotSendTheSameIntentTwice() {
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        val marked = AtomicBoolean(false)
        every { intent.getBooleanExtra(any(), false) } answers { marked.get() }
        val preparationEntered = CountDownLatch(1)
        val releasePreparation = CountDownLatch(1)
        val sends = AtomicInteger()
        val smsMsg = hookSmsMsg(body = "code 123456", smsCode = "123456")
        val winner = ParsedCodeSmsForwarder(
            smsForwardPreparer = { _, _, _, sourceIntent, eventId ->
                preparationEntered.countDown()
                check(releasePreparation.await(5, TimeUnit.SECONDS))
                XpDispatchCoordinator.prepareParsedSms(smsMsg, sourceIntent, eventId)
            },
            smsForwardDispatcher = { _, _, _ -> sends.incrementAndGet(); true },
            parsedForwardMarker = { marked.set(true) },
        )
        val loser = ParsedCodeSmsForwarder(
            smsForwardPreparer = { _, _, _, _, _ -> error("Another instance owns this Intent") },
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit<Boolean> {
                winner.forwardIfCodeSms(pluginContext, phoneContext, smsMsg, intent, "event")
            }
            assertTrue(preparationEntered.await(5, TimeUnit.SECONDS))
            assertFalse(loser.forwardIfCodeSms(pluginContext, phoneContext, smsMsg, intent, "event"))
            assertEquals(0, sends.get())
            releasePreparation.countDown()
            assertTrue(pending.get(5, TimeUnit.SECONDS))
            assertTrue(loser.forwardIfCodeSms(pluginContext, phoneContext, smsMsg, intent, "event"))
            assertEquals(1, sends.get())
        } finally {
            releasePreparation.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun failedAttemptReleasesIntentForAnotherInstance() {
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        val smsMsg = hookSmsMsg(body = "code 123456", smsCode = "123456")
        var attempts = 0
        fun forwarder() = ParsedCodeSmsForwarder(
            smsForwardPreparer = { _, _, _, sourceIntent, eventId ->
                XpDispatchCoordinator.prepareParsedSms(smsMsg, sourceIntent, eventId)
            },
            smsForwardDispatcher = { _, _, _ -> ++attempts > 1 },
            parsedForwardMarker = {},
        )
        assertFalse(forwarder().forwardIfCodeSms(pluginContext, phoneContext, smsMsg, intent, "event"))
        assertTrue(forwarder().forwardIfCodeSms(pluginContext, phoneContext, smsMsg, intent, "event"))
        assertEquals(2, attempts)
    }

    @Test
    fun preparationExceptionReleasesIntentForRetry() {
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        val smsMsg = hookSmsMsg(body = "code 123456", smsCode = "123456")
        var preparations = 0
        val forwarder = ParsedCodeSmsForwarder(
            smsForwardPreparer = { _, _, _, sourceIntent, eventId ->
                preparations += 1
                if (preparations == 1) error("temporary")
                XpDispatchCoordinator.prepareParsedSms(smsMsg, sourceIntent, eventId)
            },
            smsForwardDispatcher = { _, _, _ -> true },
            parsedForwardMarker = {},
        )
        assertFalse(forwarder.forwardIfCodeSms(pluginContext, phoneContext, smsMsg, intent, "event"))
        assertTrue(forwarder.forwardIfCodeSms(pluginContext, phoneContext, smsMsg, intent, "event"))
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
