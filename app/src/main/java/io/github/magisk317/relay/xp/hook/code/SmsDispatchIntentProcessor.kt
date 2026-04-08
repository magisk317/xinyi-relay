package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import android.os.Process
import io.github.magisk317.relay.xpbridge.PreparedSmsHookDispatch
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.relay.xpbridge.XpSmsBlacklist
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.verification.SmsDispatchIntentProcessor as SharedSmsDispatchIntentProcessor
import io.github.magisk317.smscode.verification.SmsHandlerDispatchDecision
import io.github.magisk317.smscode.xposed.utils.XLog

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
    private val smsForwardPreparer: suspend (Context, Context, SmsMsg, Intent, String) -> PreparedSmsHookDispatch? =
        { resolvedPluginContext, resolvedPhoneContext, smsMsg, intent, eventId ->
            XpDispatchCoordinator.prepareIngressSms(
                pluginContext = resolvedPluginContext,
                phoneContext = resolvedPhoneContext,
                smsMsg = smsMsg,
                sourceIntent = intent,
                eventId = eventId,
            )
        },
    private val smsForwardDispatcher: (Context, PreparedSmsHookDispatch, String) -> Boolean =
        { resolvedPluginContext, prepared, eventId ->
            val dispatchResult = XpDispatchCoordinator.dispatchPreparedSms(
                context = resolvedPluginContext,
                prepared = prepared,
                sentFromUid = Process.myUid(),
            )
            if (!dispatchResult.dispatched) {
                XLog.e(
                    "SmsDispatchIntentProcessor: IPC token empty, skip direct sms forward. event_id=%s",
                    eventId,
                )
                false
            } else {
                if (dispatchResult.bypassUsed) {
                    XLog.w(
                        "SmsDispatchIntentProcessor: IPC token empty, continue with receiver-side bypass. event_id=%s uid=%d",
                        eventId,
                        Process.myUid(),
                    )
                }
                XLog.i(
                    "SmsDispatchIntentProcessor dispatched parsed sms forward: event_id=%s code_present=%s tokenPresent=%s",
                    eventId,
                    prepared.smsMsg.smsCode?.isNotBlank() == true,
                    dispatchResult.tokenPresent,
                )
                true
            }
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
    private val parsedCodeSmsForwarder = ParsedCodeSmsForwarder(
        smsForwardPreparer = smsForwardPreparer,
        smsForwardDispatcher = smsForwardDispatcher,
    )

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
        val parseResult = outcome.parseResult as? ParseResult
        val smsMsg = outcome.smsMsg
        if (smsMsg != null && parseResult != null) {
            parsedCodeSmsForwarder.forwardIfCodeSms(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                sourceIntent = intent,
                eventId = eventId,
            )
        }
        return Outcome(
            smsMsg = outcome.smsMsg,
            blacklistResult = outcome.blacklistResult,
            parseResult = parseResult,
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
