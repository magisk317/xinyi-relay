package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.relay.xpbridge.XpSmsBlacklist
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.verification.SmsDispatchIntentProcessor as SharedSmsDispatchIntentProcessor
import io.github.magisk317.smscode.verification.SmsHandlerDispatchDecision

internal class SmsDispatchIntentProcessor(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val incomingSmsParser: (Intent) -> SmsMsg? = XpDispatchCoordinator::parseIncomingSms,
    private val blacklistMatcher: (Context, String?, String?) -> BlacklistMatchResult = { context, sender, body ->
        XpSmsBlacklist.match(context, sender, body).toShared()
    },
    private val codeParser: (Context, Context, Intent, String) -> ParseResult? = { pluginContext, phoneContext, intent, eventId ->
        CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
    },
    private val delegateFactory: (
        Context,
        Context,
        (Intent) -> SmsMsg?,
        (Context, String?, String?) -> BlacklistMatchResult,
        (Context, Context, Intent, String) -> ParseResult?,
    ) -> SharedSmsDispatchIntentProcessor<SmsMsg> = { resolvedPluginContext, resolvedPhoneContext, incomingParser, matcher, parser ->
        SharedSmsDispatchIntentProcessor(
            pluginContext = resolvedPluginContext,
            phoneContext = resolvedPhoneContext,
            incomingSmsParser = incomingParser,
            blacklistMatcher = matcher,
            codeParser = parser,
        )
    },
) {
    data class Outcome(
        val smsMsg: SmsMsg?,
        val blacklistResult: BlacklistMatchResult,
        val parseResult: ParseResult?,
        val decision: SmsHandlerDispatchDecision.Decision,
    )

    fun handle(intent: Intent, eventId: String): Outcome {
        val outcome = delegateFactory(
            pluginContext,
            phoneContext,
            incomingSmsParser,
            blacklistMatcher,
            codeParser,
        ).handle(intent, eventId)
        return Outcome(
            smsMsg = outcome.smsMsg,
            blacklistResult = outcome.blacklistResult,
            parseResult = outcome.parseResult as? ParseResult,
            decision = outcome.decision,
        )
    }
}

private fun XpSmsBlacklist.MatchResult.toShared(): BlacklistMatchResult {
    return BlacklistMatchResult(
        matched = matched,
        matchType = matchType,
        pattern = pattern,
        actionDelete = actionDelete,
        actionBlock = actionBlock,
    )
}
