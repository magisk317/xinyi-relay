package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeContext
import io.github.magisk317.relay.xp.hook.forward.SmsForwardSimRoutingResolver
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.smscode.verification.DispatchGateDecision
import io.github.magisk317.smscode.runtime.verification.SmsDispatchIntentProcessor as SharedSmsDispatchIntentProcessor
import io.github.magisk317.smscode.runtime.verification.SmsDispatchIntentHandler as SharedSmsDispatchIntentHandler
import io.github.magisk317.smscode.runtime.verification.defaultGateDecision

/**
 * Host adapter over the shared [SharedSmsDispatchIntentHandler]; stop reasons and
 * outcomes reuse the shared types directly.
 */
internal class SmsDispatchIntentHandler(
    private val runtimeResolver: (String) -> SmsHookRuntimeContext?,
    private val moduleEnabledReader: (Context) -> Boolean = XpPrefs::isEnabled,
    private val conflictSuppressor: (Context, String) -> Boolean = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    private val dispatchProcessor: (Context, Context, Intent, String) -> SmsDispatchIntentProcessor.Outcome =
        { pluginContext, phoneContext, intent, eventId ->
            SmsDispatchIntentProcessor(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
            ).handle(intent, eventId)
        },
    private val conflictNotifier: (Context, Context, String, String) -> Unit =
        SmsCodeConflictNoticeHelper::notifyConflictOnSms,
    private val suppressionLogger: (String) -> Unit = {},
    private val preDispatchIntentMutator: (Intent, Any?, Array<Any?>?) -> Unit = { intent, inboundSmsHandler, hookArgs ->
        SmsForwardSimRoutingResolver.ensureSimRoutingExtras(
            intent = intent,
            handler = inboundSmsHandler,
            args = hookArgs,
        )
    },
    private val blacklistDeleteScheduler: (Context, Context, SmsMsg) -> Unit = { _, _, _ -> },
    private val inboundBlocker: (Any, Any, String, String) -> Unit = { _, _, _, _ -> },
    private val gateEvaluator: (Boolean, Boolean) -> DispatchGateDecision = ::defaultGateDecision,
) {
    private val delegate by lazy {
        SharedSmsDispatchIntentHandler(
            runtimeResolver = runtimeResolver,
            moduleEnabledReader = moduleEnabledReader,
            conflictSuppressor = conflictSuppressor,
            dispatchProcessor = { pluginContext, phoneContext, intent, eventId ->
                val outcome = dispatchProcessor(pluginContext, phoneContext, intent, eventId)
                SharedSmsDispatchIntentProcessor.Outcome(
                    smsMsg = outcome.smsMsg,
                    blacklistResult = outcome.blacklistResult,
                    parseResult = outcome.parseResult,
                    decision = outcome.decision,
                )
            },
            conflictNotifier = conflictNotifier,
            suppressionLogger = suppressionLogger,
            blacklistDeleteScheduler = blacklistDeleteScheduler,
            blacklistHitRecorder = { pluginContext, _, smsMsg, blacklistResult, decision, eventId ->
                SmsBlacklistHitRecorder.record(
                    pluginContext = pluginContext,
                    smsMsg = smsMsg,
                    blacklistResult = blacklistResult,
                    decision = decision,
                    eventId = eventId,
                    source = "dispatch_intent",
                )
            },
            inboundBlocker = inboundBlocker,
            gateEvaluator = gateEvaluator,
        )
    }

    fun handle(
        intent: Intent,
        eventId: String,
        inboundSmsHandler: Any?,
        receiver: Any?,
        hookArgs: Array<Any?>? = null,
    ): SharedSmsDispatchIntentHandler.Outcome {
        preDispatchIntentMutator(intent, inboundSmsHandler, hookArgs)
        return delegate.handle(
            intent = intent,
            eventId = eventId,
            inboundSmsHandler = inboundSmsHandler,
            receiver = receiver,
        )
    }
}
