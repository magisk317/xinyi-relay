package io.github.magisk317.relay.xp.hook.forward

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.xp.hook.code.SmsBlacklistHitRecorder
import io.github.magisk317.relay.xp.hook.code.SmsBlockEvaluator
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.smscode.verification.SmsIntentHookSupport
import io.github.magisk317.smscode.xposed.utils.XLog

internal class SmsForwardBlockHandler(
    private val blockEvaluator: (Context, Intent, String, String) -> SmsBlockEvaluator.Result? =
        SmsBlockEvaluator::evaluate,
    private val blacklistHitRecorder: (
        Context,
        SmsMsg?,
        io.github.magisk317.smscode.verification.BlacklistMatchResult,
        io.github.magisk317.smscode.verification.SmsHandlerDispatchDecision.Decision,
        String,
        String,
    ) -> Unit = SmsBlacklistHitRecorder::record,
    private val blacklistDeleteScheduler: (Context, Context, SmsMsg) -> Unit,
    private val inboundBlocker: (Any, Any, String, String) -> Boolean,
    private val parsedCodeForwarder: (Context, Context, SmsMsg, Intent, String) -> Boolean,
    private val receiverResolver: (Array<Any?>?) -> Any? = SmsIntentHookSupport::findRawTableReceiver,
    private val defaultResultForType: (Class<*>?) -> Any? = SmsIntentHookSupport::defaultResultForType,
) {
    data class Request(
        val pluginContext: Context,
        val phoneContext: Context,
        val intent: Intent,
        val eventId: String,
        val inboundSmsHandler: Any?,
        val hookArgs: Array<Any?>?,
        val methodReturnType: Class<*>?,
    )

    data class Outcome(
        val suppressForward: Boolean = false,
        val inboundBlocked: Boolean = false,
        val shouldSetMethodResult: Boolean = false,
        val methodResult: Any? = null,
    )

    fun handle(request: Request): Outcome {
        val evaluation = blockEvaluator(
            request.pluginContext,
            request.intent,
            request.eventId,
            SOURCE_SMS_FORWARD,
        ) ?: return Outcome()

        blacklistHitRecorder(
            request.pluginContext,
            evaluation.smsMsg,
            evaluation.blacklistResult,
            evaluation.decision,
            request.eventId,
            SOURCE_SMS_FORWARD,
        )
        if (evaluation.blacklistDeleteOnly && evaluation.smsMsg != null) {
            blacklistDeleteScheduler(request.pluginContext, request.phoneContext, evaluation.smsMsg)
        }

        val reason = evaluation.blockReason ?: return Outcome()
        XLog.w("SmsForwardHook block start: reason=%s event_id=%s", reason, request.eventId)
        val inbound = request.inboundSmsHandler
        val receiver = receiverResolver(request.hookArgs)
        if (inbound == null || receiver == null) {
            XLog.w("SmsForwardHook block fallback: receiver unavailable event_id=%s", request.eventId)
            return Outcome(suppressForward = true)
        }

        val blocked = inboundBlocker(inbound, receiver, reason, request.eventId)
        if (!blocked) {
            XLog.w("SmsForwardHook block aborted: cleanup incomplete event_id=%s", request.eventId)
            return Outcome(suppressForward = true)
        }
        if (reason == SmsBlockEvaluator.BLOCK_REASON_PREF_BLOCK) {
            evaluation.smsMsg?.let { smsMsg ->
                parsedCodeForwarder(
                    request.pluginContext,
                    request.phoneContext,
                    smsMsg,
                    request.intent,
                    request.eventId,
                )
            }
        }
        return Outcome(
            suppressForward = true,
            inboundBlocked = true,
            shouldSetMethodResult = true,
            methodResult = defaultResultForType(request.methodReturnType),
        )
    }

    private companion object {
        private const val SOURCE_SMS_FORWARD = "sms_forward"
    }
}
