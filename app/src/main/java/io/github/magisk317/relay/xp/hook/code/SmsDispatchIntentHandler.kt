package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.xp.SmsMsg
import io.github.magisk317.relay.xp.XpPrefs
import io.github.magisk317.relay.xp.hook.SmsHookDispatchGate
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeContext
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.smscode.core.utils.XLog

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
    private val blacklistDeleteScheduler: (Context, Context, SmsMsg) -> Unit = { _, _, _ -> },
    private val inboundBlocker: (Any, Any, String, String) -> Unit = { _, _, _, _ -> },
) {
    enum class StopReason {
        RUNTIME_UNAVAILABLE,
        MODULE_DISABLED,
        CONFLICT_SUPPRESSED,
        SMS_BLOCKED,
    }

    data class Outcome(
        val stopReason: StopReason? = null,
        val inboundBlocked: Boolean = false,
    ) {
        val shouldStopDispatch: Boolean = stopReason != null
    }

    fun handle(
        intent: Intent,
        eventId: String,
        inboundSmsHandler: Any?,
        receiver: Any?,
    ): Outcome {
        val runtime = runtimeResolver(DISPATCH_HEARTBEAT_SOURCE)
        if (runtime == null) {
            XLog.e("Context is null, skip parsing. pluginContext: %s, phoneContext: %s", null, null)
            return Outcome(stopReason = StopReason.RUNTIME_UNAVAILABLE)
        }
        val pluginContext = runtime.pluginContext
        val phoneContext = runtime.phoneContext
        when (
            SmsHookDispatchGate.evaluate(
                moduleEnabled = moduleEnabledReader(pluginContext),
                relayFeatureRequired = false,
                relayFeaturesEnabled = true,
                suppressedByRelay = conflictSuppressor(phoneContext, DISPATCH_CONFLICT_SOURCE),
            ).reason
        ) {
            SmsHookDispatchGate.BlockReason.MODULE_DISABLED -> {
                XLog.w("Diag: module disabled in settings")
                XLog.i("XposedSmsCode disabled, exiting")
                return Outcome(stopReason = StopReason.MODULE_DISABLED)
            }

            SmsHookDispatchGate.BlockReason.CONFLICT_SUPPRESSED -> {
                suppressionLogger(DISPATCH_STAGE)
                conflictNotifier(
                    pluginContext,
                    phoneContext,
                    eventId,
                    DISPATCH_CONFLICT_SOURCE,
                )
                return Outcome(stopReason = StopReason.CONFLICT_SUPPRESSED)
            }

            else -> Unit
        }

        val dispatchOutcome = dispatchProcessor(pluginContext, phoneContext, intent, eventId)
        val smsMsg = dispatchOutcome.smsMsg
        val decision = dispatchOutcome.decision
        if (decision.shouldDeleteByBlacklist && smsMsg != null) {
            blacklistDeleteScheduler(pluginContext, phoneContext, smsMsg)
        }
        decision.blockReason?.let { blockReason ->
            XLog.w("Diag sms block reason=%s event_id=%s", blockReason.wireValue, eventId)
            if (inboundSmsHandler != null && receiver != null) {
                inboundBlocker(
                    inboundSmsHandler,
                    receiver,
                    blockReason.wireValue,
                    eventId,
                )
                return Outcome(
                    stopReason = StopReason.SMS_BLOCKED,
                    inboundBlocked = true,
                )
            }
            return Outcome(stopReason = StopReason.SMS_BLOCKED)
        }
        if (decision.shouldAllowSystemPersist) {
            XLog.w(
                "Diag allow system inbox persist: event_id=%s sender_hash=%s body_len=%d",
                eventId,
                senderHash(smsMsg?.sender),
                smsMsg?.body?.length ?: 0,
            )
        }
        return Outcome()
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    private companion object {
        private const val DISPATCH_HEARTBEAT_SOURCE = "sms_handler_dispatch"
        private const val DISPATCH_CONFLICT_SOURCE = "SmsHandlerHook#dispatchIntent"
        private const val DISPATCH_STAGE = "dispatchIntent"
    }
}
