package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.os.Handler
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpSharedRuntimeGate
import io.github.magisk317.relay.xp.hook.code.action.impl.AutoInputAction
import io.github.magisk317.relay.xp.hook.code.action.impl.CopyToClipboardAction
import io.github.magisk317.relay.xp.hook.code.action.impl.NotifyAction
import io.github.magisk317.relay.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.relay.xp.hook.code.action.impl.RecordSmsAction
import io.github.magisk317.relay.xp.hook.code.action.impl.ToastAction
import io.github.magisk317.smscode.runtime.verification.AutoInputDispatchGuard
import io.github.magisk317.smscode.runtime.verification.NotificationDispatchGuard
import io.github.magisk317.smscode.runtime.verification.SmsCodeActionScheduler
import io.github.magisk317.smscode.runtime.verification.SmsCodeActionDispatcher as SharedSmsCodeActionDispatcher
import io.github.magisk317.smscode.runtime.verification.SmsCodePostParseCoordinator
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledExecutorService

internal object SmsCodeActionDispatcher {
    fun dispatchParsedSmsActions(
        uiHandler: Handler,
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ParsedSmsPlan,
        uiDispatcher: (
            Handler,
            Context,
            Context,
            SmsMsg,
            SmsCodePostParseCoordinator.UiPlan,
        ) -> Unit = { handler, plugin, phone, message, uiPlan ->
            dispatchUiActions(
                uiHandler = handler,
                pluginContext = plugin,
                phoneContext = phone,
                smsMsg = message,
                uiPlan = uiPlan,
            )
        },
        autoInputScheduler: (
            ScheduledExecutorService,
            Context,
            Context,
            SmsMsg,
            Long,
            Boolean,
            Long?,
        ) -> Unit = ::scheduleAutoInput,
        notificationScheduler: (
            ScheduledExecutorService,
            Context,
            Context,
            SmsMsg,
            SmsCodePostParseCoordinator.NotificationPlan,
        ) -> Unit = ::scheduleNotification,
        recordScheduler: (
            ScheduledExecutorService,
            Context,
            Context,
            SmsMsg,
            String,
            Boolean,
        ) -> Unit = ::scheduleRecord,
        operateSmsScheduler: (
            ScheduledExecutorService,
            Context,
            Context,
            SmsMsg,
            List<Long>,
    ) -> Unit = ::scheduleOperateSmsActions,
    ) {
        SharedSmsCodeActionDispatcher.dispatchParsedSmsActions(
            uiHandler = uiHandler,
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            plan = plan,
            uiDispatcher = uiDispatcher,
            autoInputScheduler = autoInputScheduler,
            notificationScheduler = notificationScheduler,
            recordScheduler = recordScheduler,
            operateSmsScheduler = operateSmsScheduler,
        )
    }

    fun dispatchObservedSmsActions(
        executor: ScheduledExecutorService?,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ObservedSmsPlan,
        autoInputRunner: (Context, Context, SmsMsg, Boolean, Long?) -> Unit = ::runAutoInputNow,
        autoInputScheduler: (ScheduledExecutorService, Context, Context, SmsMsg, Long, Boolean, Long?) -> Unit = ::scheduleAutoInput,
        recordRunner: (Context, Context, SmsMsg, String, Boolean) -> Unit = ::runRecordNow,
    ) {
        SharedSmsCodeActionDispatcher.dispatchObservedSmsActions(
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            plan = plan,
            autoInputRunner = autoInputRunner,
            autoInputScheduler = autoInputScheduler,
            recordRunner = recordRunner,
        )
    }

    fun resolveToastDelayMs(autoInputDelayMs: Long?): Long {
        return SmsCodeActionScheduler.resolveToastDelayAfterAutoInput(autoInputDelayMs)
    }

    private fun dispatchUiActions(
        uiHandler: Handler,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        uiPlan: SmsCodePostParseCoordinator.UiPlan,
    ) {
        if (!mobileAutomationAllowed(pluginContext)) return
        uiHandler.post(
            CopyToClipboardAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = uiPlan.copyToClipboardEnabled,
            ),
        )
        if (!uiPlan.showToast) return

        val toastAction = ToastAction(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            enabled = true,
        )
        uiHandler.post(toastAction)
    }

    fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        deduplicateEnabled: Boolean,
        attemptId: Long? = null,
    ) {
        SmsCodeActionScheduler.runAutoInputNowIfClaimed(
            pluginContext = pluginContext,
            smsMsg = smsMsg,
            claimDelayMs = 0L,
            claimAutoInputDispatch = ::claimAutoInputDispatch,
        ) {
            AutoInputAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                deduplicateEnabled = deduplicateEnabled,
                dispatchDelayMs = 0L,
                attemptId = attemptId,
            )
        }
    }

    fun scheduleAutoInput(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
        deduplicateEnabled: Boolean,
        attemptId: Long? = null,
    ) {
        SmsCodeActionScheduler.scheduleAutoInputIfClaimed(
            executor = executor,
            pluginContext = pluginContext,
            smsMsg = smsMsg,
            delayMs = delayMs,
            claimAutoInputDispatch = ::claimAutoInputDispatch,
        ) {
            AutoInputAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                deduplicateEnabled = deduplicateEnabled,
                dispatchDelayMs = delayMs,
                attemptId = attemptId,
            )
        }
    }

    fun runRecordNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        RecordSmsAction(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            enabled = true,
            deduplicateEnabled = deduplicateEnabled,
        ).call()
    }

    fun scheduleRecord(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        SmsCodeActionScheduler.scheduleNow(executor) {
            RecordSmsAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                eventId = eventId,
                enabled = true,
                deduplicateEnabled = deduplicateEnabled,
            )
        }
    }

    private fun scheduleNotification(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        plan: SmsCodePostParseCoordinator.NotificationPlan,
    ) {
        if (!mobileAutomationAllowed(pluginContext)) return
        if (XpPrefs.deduplicateSms(pluginContext) && !claimNotificationDispatch(pluginContext, smsMsg)) return
        SmsCodeActionScheduler.scheduleNow(executor) {
            NotifyAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = true,
                autoCancelEnabled = plan.autoCancelDelayMs != null,
                retentionTimeMs = plan.autoCancelDelayMs ?: 0L,
            )
        }
    }

    private fun scheduleOperateSmsActions(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delays: List<Long>,
    ) {
        SmsCodeActionScheduler.scheduleEachDelay(
            executor = executor,
            delays = delays,
        ) {
            Callable {
                if (mobileAutomationAllowed(pluginContext)) {
                    OperateSmsAction(pluginContext, phoneContext, smsMsg).call()
                }
            }
        }
    }

    private fun claimNotificationDispatch(
        pluginContext: Context,
        smsMsg: SmsMsg,
    ): Boolean {
        return NotificationDispatchGuard.claim(
            pluginContext = pluginContext,
            smsMsg = smsMsg,
        ) { context, fileName, keys, windowMs, maxEntries ->
            XpSharedRuntimeGate.claimAllWithinWindow(
                context = context,
                fileName = fileName,
                keys = keys,
                windowMs = windowMs,
                maxEntries = maxEntries,
            ).let { result ->
                NotificationDispatchGuard.ClaimResult(
                    claimed = result.claimed,
                    ageMs = result.ageMs,
                    key = result.key,
                )
            }
        }
    }

    internal fun claimAutoInputDispatch(
        pluginContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
        gateClaimer: (Context, String, List<String>, Long, Int) -> XpSharedRuntimeGate.ClaimResult =
            { context, fileName, keys, windowMs, maxEntries ->
                XpSharedRuntimeGate.claimAllWithinWindow(
                    context = context,
                    fileName = fileName,
                    keys = keys,
                    windowMs = windowMs,
                    maxEntries = maxEntries,
                )
            },
    ): Boolean {
        if (!mobileAutomationAllowed(pluginContext)) return false
        return AutoInputDispatchGuard.claim(
            pluginContext = pluginContext,
            smsMsg = smsMsg,
            delayMs = delayMs,
        ) { context, fileName, keys, windowMs, maxEntries ->
            gateClaimer(
                context,
                fileName,
                keys,
                windowMs,
                maxEntries,
            ).toAutoInputClaim()
        }
    }

    private fun mobileAutomationAllowed(context: Context): Boolean =
        XpPrefs.mobileAutomationAllowed(context)

    private fun XpSharedRuntimeGate.ClaimResult.toAutoInputClaim(): AutoInputDispatchGuard.ClaimResult {
        return AutoInputDispatchGuard.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
            key = key,
        )
    }
}
