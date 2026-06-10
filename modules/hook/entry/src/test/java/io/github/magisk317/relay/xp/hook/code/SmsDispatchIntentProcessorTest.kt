package io.github.magisk317.relay.xp.hook.code

import android.content.Intent
import io.mockk.every
import io.mockk.verify
import io.github.magisk317.relay.xp.hook.EXTRA_PARSED_SMS_FORWARD_DISPATCHED
import io.github.magisk317.relay.testing.clearXpLogSink
import io.github.magisk317.relay.testing.hookSmsMsg
import io.github.magisk317.relay.testing.installSilentXpLogSink
import io.github.magisk317.relay.testing.relaxedHookContexts
import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.xpbridge.PreparedSmsHookDispatch
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsDispatchIntentProcessorTest {

    @AfterEach
    fun tearDown() {
        clearXpLogSink()
    }

    @Test
    fun handle_passesParsedSmsIntoBlacklistAndDecisionPipeline() {
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        var matchedSender: String? = null
        var matchedBody: String? = null
        val parseResult = ParseResult().apply { isBlockSms = true }
        val processor = SmsDispatchIntentProcessor(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            incomingSmsParser = {
                hookSmsMsg()
            },
            blacklistMatcher = { _, sender, body ->
                matchedSender = sender
                matchedBody = body
                BlacklistMatchResult(
                    matched = true,
                    matchType = "number",
                    pattern = "1068",
                    actionDelete = true,
                    actionBlock = false,
                )
            },
            codeParser = { _, _, _, _ -> parseResult },
        )

        val outcome = processor.handle(intent, "evt-1")

        assertEquals("1068", matchedSender)
        assertEquals("otp 123456", matchedBody)
        assertEquals("1068", outcome.smsMsg?.sender)
        assertTrue(outcome.blacklistResult.matched)
        assertEquals(parseResult, outcome.parseResult)
        assertTrue(outcome.decision.shouldDeleteByBlacklist)
        assertNull(outcome.decision.blockReason)
    }

    @Test
    fun handle_reportsNullParseResultWhenCodeWorkerMisses() {
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        val processor = SmsDispatchIntentProcessor(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            incomingSmsParser = { null },
            blacklistMatcher = { _, _, _ -> BlacklistMatchResult(matched = false) },
            codeParser = { _, _, _, _ -> null },
        )

        val outcome = processor.handle(intent, "evt-2")

        assertNull(outcome.smsMsg)
        assertFalse(outcome.blacklistResult.matched)
        assertNull(outcome.parseResult)
        assertFalse(outcome.decision.shouldDeleteByBlacklist)
        assertNull(outcome.decision.blockReason)
        assertFalse(outcome.decision.shouldAllowSystemPersist)
    }

    @Test
    fun handle_dispatchesDirectSmsForwardWhenCodeParsed() {
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        every { intent.putExtra(EXTRA_PARSED_SMS_FORWARD_DISPATCHED, true) } returns intent
        val smsMsg = hookSmsMsg()
        val parseResult = ParseResult().apply { isBlockSms = false }
        val preparedSmsMsg = smsMsg.copy(
            smsCode = "123456",
            packageName = "com.bank.app",
        )
        var preparedIntent: Intent? = null
        var preparedEventId: String? = null
        var dispatchedPrepared: PreparedSmsHookDispatch? = null
        var dispatchedEventId: String? = null
        val processor = SmsDispatchIntentProcessor(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            incomingSmsParser = { smsMsg },
            blacklistMatcher = { _, _, _ -> BlacklistMatchResult(matched = false) },
            codeParser = { _, _, _, _ -> parseResult },
            smsForwardPreparer = { _, _, incomingSms, sourceIntent, eventId ->
                preparedIntent = sourceIntent
                preparedEventId = eventId
                XpDispatchCoordinator.prepareParsedSms(
                    smsMsg = preparedSmsMsg,
                    sourceIntent = sourceIntent,
                    eventId = eventId,
                )
            },
            smsForwardDispatcher = { _, prepared, eventId ->
                dispatchedPrepared = prepared
                dispatchedEventId = eventId
                true
            },
        )

        val outcome = processor.handle(intent, "evt-3")

        assertEquals(parseResult, outcome.parseResult)
        assertSame(intent, preparedIntent)
        assertEquals("evt-3", preparedEventId)
        assertEquals("123456", dispatchedPrepared?.smsMsg?.smsCode)
        assertEquals("com.bank.app", dispatchedPrepared?.smsMsg?.packageName)
        assertEquals("evt-3", dispatchedEventId)
        verify { intent.putExtra(EXTRA_PARSED_SMS_FORWARD_DISPATCHED, true) }
    }

    @Test
    fun handle_doesNotMarkIntentWhenDirectSmsForwardFails() {
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        val smsMsg = hookSmsMsg()
        val processor = SmsDispatchIntentProcessor(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            incomingSmsParser = { smsMsg },
            blacklistMatcher = { _, _, _ -> BlacklistMatchResult(matched = false) },
            codeParser = { _, _, _, _ -> ParseResult().apply { isBlockSms = false } },
            smsForwardPreparer = { _, _, _, sourceIntent, eventId ->
                XpDispatchCoordinator.prepareParsedSms(
                    smsMsg = smsMsg.copy(smsCode = "123456"),
                    sourceIntent = sourceIntent,
                    eventId = eventId,
                )
            },
            smsForwardDispatcher = { _, _, _ -> false },
        )

        processor.handle(intent, "evt-3b")

        verify(exactly = 0) { intent.putExtra(EXTRA_PARSED_SMS_FORWARD_DISPATCHED, true) }
    }

    @Test
    fun handle_skipsDirectSmsForwardWhenPreparedTypeIsNotSmsCode() {
        installSilentXpLogSink()
        val (pluginContext, phoneContext) = relaxedHookContexts()
        val intent = relaxedIntent()
        val smsMsg = hookSmsMsg(body = "plain body")
        var dispatched = false
        val processor = SmsDispatchIntentProcessor(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            incomingSmsParser = { smsMsg },
            blacklistMatcher = { _, _, _ -> BlacklistMatchResult(matched = false) },
            codeParser = { _, _, _, _ -> ParseResult().apply { isBlockSms = false } },
            smsForwardPreparer = { _, _, _, _, eventId ->
                XpDispatchCoordinator.prepareParsedSms(
                    smsMsg = smsMsg,
                    sourceIntent = intent,
                    eventId = eventId,
                )
            },
            smsForwardDispatcher = { _, _, _ ->
                dispatched = true
                true
            },
        )

        processor.handle(intent, "evt-4")

        assertFalse(dispatched)
    }
}
