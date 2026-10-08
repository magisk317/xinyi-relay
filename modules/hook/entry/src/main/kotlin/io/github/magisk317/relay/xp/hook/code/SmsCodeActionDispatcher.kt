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
import io.github.magisk317.smscode.runtime.verification.HostSmsCodeActionWiring
import io.github.magisk317.smscode.runtime.verification.NotificationDispatchGuard
import io.github.magisk317.smscode.runtime.verification.SmsCodeActionScheduler
import io.github.magisk317.smscode.runtime.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.runtime.verification.SmsCodeActionDispatcher as SharedSmsCodeActionDispatcher
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.ScheduledExecutorService

internal object SmsCodeActionDispatcher {

    private val wiring: HostSmsCodeActionWiring<SmsMsg> = HostSmsCodeActionWiring(
        mobileAutomationAllowed = XpPrefs::mobileAutomationAllowed,
        claimAutoInputDispatch = ::claimAutoInputDispatch,
        claimNotificationDispatch = ::claimNotificationDispatch,
        newCopyAction = { plugin, phone, msg, enabled ->
            CopyToClipboardAction(
                pluginContext = plugin,
                phoneContext = phone,
                smsMsg = msg,
                enabled = enabled,
            )
        },
        newToastAction = { plugin, phone, msg, enabled ->
            if (!enabled) {
                null
            } else {
                ToastAction(
                    pluginContext = plugin,
                    phoneContext = phone,
                    smsMsg = msg,
                    enabled = true,
                )
            }
        },
        newAutoInputAction = { plugin, phone, msg, deduplicateEnabled, dispatchDelayMs, attemptId ->
            AutoInputAction(
                pluginContext = plugin,
                phoneContext = phone,
                smsMsg = msg,
                deduplicateEnabled = deduplicateEnabled,
                dispatchDelayMs = dispatchDelayMs,
                attemptId = attemptId,
            )
        },
        newRecordAction = { plugin, phone, msg, eventId, deduplicateEnabled ->
            {
                RecordSmsAction(
                    pluginContext = plugin,
                    phoneContext = phone,
                    smsMsg = msg,
                    eventId = eventId,
                    enabled = true,
                    deduplicateEnabled = deduplicateEnabled,
                ).call()
            }
        },
        newNotifyAction = { plugin, phone, msg, autoCancelEnabled, retentionTimeMs ->
            {
                NotifyAction(
                    pluginContext = plugin,
                    phoneContext = phone,
                    smsMsg = msg,
                    enabled = true,
                    autoCancelEnabled = autoCancelEnabled,
                    retentionTimeMs = retentionTimeMs,
                ).call()
            }
        },
        newOperateSmsAction = { plugin, phone, msg ->
            {
                OperateSmsAction(plugin, phone, msg).call()
            }
        },
    )

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
            wiring.dispatchUiActions(handler, plugin, phone, message, uiPlan)
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
        ) -> Unit = { executor, plugin, phone, message, notificationPlan ->
            wiring.scheduleNotification(
                executor,
                plugin,
                phone,
                message,
                notificationPlan,
                XpPrefs.deduplicateSms(plugin),
            )
        },
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
        autoInputRunner: (Context, Context, SmsMsg, Boolean, Long?) -> Unit = wiring::runAutoInputNow,
        autoInputScheduler: (ScheduledExecutorService, Context, Context, SmsMsg, Long, Boolean, Long?) -> Unit = wiring::scheduleAutoInput,
        recordRunner: (Context, Context, SmsMsg, String, Boolean) -> Unit = wiring::runRecordNow,
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

    fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        deduplicateEnabled: Boolean,
        attemptId: Long? = null,
    ) {
        wiring.runAutoInputNow(pluginContext, phoneContext, smsMsg, deduplicateEnabled, attemptId)
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
        wiring.scheduleAutoInput(executor, pluginContext, phoneContext, smsMsg, delayMs, deduplicateEnabled, attemptId)
    }

    fun runRecordNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        wiring.runRecordNow(pluginContext, phoneContext, smsMsg, eventId, deduplicateEnabled)
    }

    fun scheduleRecord(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        wiring.scheduleRecord(executor, pluginContext, phoneContext, smsMsg, eventId, deduplicateEnabled)
    }

    fun scheduleOperateSmsActions(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delays: List<Long>,
    ) {
        wiring.scheduleOperateSmsActions(executor, pluginContext, phoneContext, smsMsg, delays)
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
        if (!XpPrefs.mobileAutomationAllowed(pluginContext)) {
            XLog.i("Mobile entitlement gate skipped auto-input dispatch")
            return false
        }
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

    private fun XpSharedRuntimeGate.ClaimResult.toAutoInputClaim(): AutoInputDispatchGuard.ClaimResult {
        return AutoInputDispatchGuard.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
            key = key,
        )
    }
}
