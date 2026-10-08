package io.github.magisk317.relay.xp.hook.forward

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.testing.clearXpLogSink
import io.github.magisk317.relay.testing.hookSmsMsg
import io.github.magisk317.relay.testing.installSilentXpLogSink
import io.github.magisk317.relay.testing.relaxedHookContexts
import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.xp.hook.code.SmsBlockEvaluator
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.runtime.verification.SmsHandlerDispatchDecision
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsForwardBlockHandlerTest {
    @AfterEach
    fun tearDown() {
        clearXpLogSink()
    }

    @Test
    fun handle_recordsAndBlocksForwardWhenBlacklistBlocks() {
        installSilentXpLogSink()
        val contexts = relaxedHookContexts()
        val smsMsg = hookSmsMsg(sender = "10001", body = "plain")
        val blacklist = blacklist(actionBlock = true)
        var recordedSource: String? = null
        var blockedReason: String? = null
        val handler = handler(
            result = result(
                smsMsg = smsMsg,
                blacklistResult = blacklist,
                blockReason = SmsBlockEvaluator.BLOCK_REASON_BLACKLIST,
                decision = decision(SmsHandlerDispatchDecision.BlockReason.BLACKLIST),
            ),
            blacklistHitRecorder = { _, recordedSms, result, recordedDecision, _, source ->
                assertSame(smsMsg, recordedSms)
                assertSame(blacklist, result)
                assertEquals(SmsHandlerDispatchDecision.BlockReason.BLACKLIST, recordedDecision.blockReason)
                recordedSource = source
            },
            inboundBlocker = { _, _, reason, eventId ->
                blockedReason = "$reason:$eventId"
                true
            },
        )

        val outcome = handler.handle(request(contexts.pluginContext, contexts.phoneContext))

        assertTrue(outcome.suppressForward)
        assertTrue(outcome.inboundBlocked)
        assertTrue(outcome.shouldSetMethodResult)
        assertEquals("default:void", outcome.methodResult)
        assertEquals("sms_forward", recordedSource)
        assertEquals("blacklist_block:evt-1", blockedReason)
    }

    @Test
    fun handle_schedulesDeleteOnlyBlacklistAndAllowsForward() {
        installSilentXpLogSink()
        val contexts = relaxedHookContexts()
        val smsMsg = hookSmsMsg(sender = "10001", body = "plain")
        var deletedSms: SmsMsg? = null
        var recorded = false
        val handler = handler(
            result = result(
                smsMsg = smsMsg,
                blacklistResult = blacklist(actionDelete = true),
                blockReason = null,
                blacklistDeleteOnly = true,
                decision = decision(blockReason = null, shouldDelete = true),
            ),
            blacklistHitRecorder = { _, _, _, _, _, _ -> recorded = true },
            blacklistDeleteScheduler = { _, _, msg -> deletedSms = msg },
        )

        val outcome = handler.handle(request(contexts.pluginContext, contexts.phoneContext))

        assertFalse(outcome.suppressForward)
        assertFalse(outcome.inboundBlocked)
        assertFalse(outcome.shouldSetMethodResult)
        assertTrue(recorded)
        assertSame(smsMsg, deletedSms)
    }

    @Test
    fun handle_forwardsParsedCodeCopyAfterPrefBlock() {
        installSilentXpLogSink()
        val contexts = relaxedHookContexts()
        val smsMsg = hookSmsMsg(sender = "10001", body = "code 123456", smsCode = "123456")
        var parsedForwardedSms: SmsMsg? = null
        val handler = handler(
            result = result(
                smsMsg = smsMsg,
                blacklistResult = blacklist(actionBlock = true),
                blockReason = SmsBlockEvaluator.BLOCK_REASON_PREF_BLOCK,
                decision = decision(SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK),
            ),
            parsedCodeForwarder = { _, _, msg, _, _ ->
                parsedForwardedSms = msg
                true
            },
            inboundBlocker = { _, _, _, _ -> true },
        )

        val outcome = handler.handle(request(contexts.pluginContext, contexts.phoneContext))

        assertTrue(outcome.suppressForward)
        assertTrue(outcome.inboundBlocked)
        assertSame(smsMsg, parsedForwardedSms)
    }

    @Test
    fun handle_suppressesForwardWithoutPretendingInboundBlockedWhenReceiverMissing() {
        installSilentXpLogSink()
        val contexts = relaxedHookContexts()
        var inboundCalled = false
        val handler = handler(
            result = result(
                smsMsg = hookSmsMsg(sender = "10001", body = "plain"),
                blacklistResult = blacklist(actionBlock = true),
                blockReason = SmsBlockEvaluator.BLOCK_REASON_BLACKLIST,
                decision = decision(SmsHandlerDispatchDecision.BlockReason.BLACKLIST),
            ),
            receiverResolver = { null },
            inboundBlocker = { _, _, _, _ ->
                inboundCalled = true
                true
            },
        )

        val outcome = handler.handle(request(contexts.pluginContext, contexts.phoneContext))

        assertTrue(outcome.suppressForward)
        assertFalse(outcome.inboundBlocked)
        assertFalse(outcome.shouldSetMethodResult)
        assertNull(outcome.methodResult)
        assertFalse(inboundCalled)
    }

    private fun handler(
        result: SmsBlockEvaluator.Result?,
        blacklistHitRecorder: (
            Context,
            SmsMsg?,
            BlacklistMatchResult,
            SmsHandlerDispatchDecision.Decision,
            String,
            String,
        ) -> Unit = { _, _, _, _, _, _ -> },
        blacklistDeleteScheduler: (Context, Context, SmsMsg) -> Unit = { _, _, _ -> },
        inboundBlocker: (Any, Any, String, String) -> Boolean = { _, _, _, _ -> false },
        parsedCodeForwarder: (Context, Context, SmsMsg, Intent, String) -> Boolean = { _, _, _, _, _ -> false },
        receiverResolver: (Array<Any?>?) -> Any? = { args -> args?.firstOrNull() },
    ): SmsForwardBlockHandler {
        return SmsForwardBlockHandler(
            blockEvaluator = { _, _, _, _ -> result },
            blacklistHitRecorder = blacklistHitRecorder,
            blacklistDeleteScheduler = blacklistDeleteScheduler,
            inboundBlocker = inboundBlocker,
            parsedCodeForwarder = parsedCodeForwarder,
            receiverResolver = receiverResolver,
            defaultResultForType = { type -> "default:${type?.simpleName ?: "none"}" },
        )
    }

    private fun request(
        pluginContext: Context,
        phoneContext: Context,
        intent: Intent = relaxedIntent(),
    ): SmsForwardBlockHandler.Request {
        return SmsForwardBlockHandler.Request(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            intent = intent,
            eventId = "evt-1",
            inboundSmsHandler = Any(),
            hookArgs = arrayOf(Any()),
            methodReturnType = Void.TYPE,
        )
    }

    private fun result(
        smsMsg: SmsMsg,
        blacklistResult: BlacklistMatchResult,
        blockReason: String?,
        blacklistDeleteOnly: Boolean = false,
        decision: SmsHandlerDispatchDecision.Decision,
    ): SmsBlockEvaluator.Result {
        return SmsBlockEvaluator.Result(
            smsMsg = smsMsg,
            blockReason = blockReason,
            blacklistDeleteOnly = blacklistDeleteOnly,
            blacklistResult = blacklistResult,
            decision = decision,
        )
    }

    private fun blacklist(
        actionDelete: Boolean = false,
        actionBlock: Boolean = false,
    ): BlacklistMatchResult {
        return BlacklistMatchResult(
            matched = true,
            matchType = "number",
            pattern = "10001",
            actionDelete = actionDelete,
            actionBlock = actionBlock,
        )
    }

    private fun decision(
        blockReason: SmsHandlerDispatchDecision.BlockReason?,
        shouldDelete: Boolean = false,
    ): SmsHandlerDispatchDecision.Decision {
        return SmsHandlerDispatchDecision.Decision(
            shouldDeleteByBlacklist = shouldDelete,
            blockReason = blockReason,
        )
    }
}
