package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.relay.xpbridge.XpSmsBlacklist
import io.github.magisk317.smscode.xposed.utils.XLog

internal class SmsDispatchIntentProcessor(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val incomingSmsParser: (Intent) -> SmsMsg? = XpDispatchCoordinator::parseIncomingSms,
    private val blacklistMatcher: (Context, String?, String?) -> XpSmsBlacklist.MatchResult = XpSmsBlacklist::match,
    private val codeParser: (Context, Context, Intent, String) -> ParseResult? = { pluginContext, phoneContext, intent, eventId ->
        CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
    },
    private val decisionEvaluator: (XpSmsBlacklist.MatchResult, Boolean, ParseResult?) -> SmsHandlerDispatchDecision.Decision =
        SmsHandlerDispatchDecision::evaluate,
) {
    data class Outcome(
        val smsMsg: SmsMsg?,
        val blacklistResult: XpSmsBlacklist.MatchResult,
        val parseResult: ParseResult?,
        val decision: SmsHandlerDispatchDecision.Decision,
    )

    fun handle(intent: Intent, eventId: String): Outcome {
        val smsMsg = incomingSmsParser(intent)
        val blacklistResult = blacklistMatcher(pluginContext, smsMsg?.sender, smsMsg?.body)
        if (blacklistResult.matched) {
            XLog.w(
                "Diag sms blacklist matched: event_id=%s type=%s, pattern=%s, delete=%s, block=%s",
                eventId,
                blacklistResult.matchType,
                blacklistResult.pattern,
                blacklistResult.actionDelete,
                blacklistResult.actionBlock,
            )
        }

        val parseResult = codeParser(pluginContext, phoneContext, intent, eventId)
        if (parseResult == null) {
            XLog.w("Diag parse result is null: event_id=%s no code matched or parse failed", eventId)
        } else {
            XLog.w("Diag parse result: event_id=%s blockSms=%s", eventId, parseResult.isBlockSms)
        }

        val decision = decisionEvaluator(
            blacklistResult,
            smsMsg != null,
            parseResult,
        )
        return Outcome(
            smsMsg = smsMsg,
            blacklistResult = blacklistResult,
            parseResult = parseResult,
            decision = decision,
        )
    }
}
