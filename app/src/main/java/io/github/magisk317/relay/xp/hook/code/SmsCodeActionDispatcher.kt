package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.os.Handler
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.impl.AutoInputAction
import io.github.magisk317.relay.xp.hook.code.action.impl.CopyToClipboardAction
import io.github.magisk317.relay.xp.hook.code.action.impl.NotifyAction
import io.github.magisk317.relay.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.relay.xp.hook.code.action.impl.RecordSmsAction
import io.github.magisk317.relay.xp.hook.code.action.impl.ToastAction
import io.github.magisk317.smscode.verification.SmsCodeActionDispatcher as SharedSmsCodeActionDispatcher
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator as SharedSmsCodePostParseCoordinator
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

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
                executor = executor,
                pluginContext = plugin,
                phoneContext = phone,
                smsMsg = message,
                uiPlan = uiPlan,
                autoInputDelayMs = plan.autoInputDelayMs,
            )
        },
        autoInputScheduler: (
            ScheduledExecutorService,
            Context,
            Context,
            SmsMsg,
            Long,
            Boolean,
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
            plan = plan.toShared(),
            uiDispatcher = { handler, plugin, phone, message, uiPlan ->
                uiDispatcher(handler, plugin, phone, message, uiPlan.toLocal())
            },
            autoInputScheduler = autoInputScheduler,
            notificationScheduler = { scheduledExecutor, plugin, phone, message, notificationPlan ->
                notificationScheduler(scheduledExecutor, plugin, phone, message, notificationPlan.toLocal())
            },
            recordScheduler = recordScheduler,
            operateSmsScheduler = operateSmsScheduler,
        )
    }

    fun dispatchObservedSmsActions(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ObservedSmsPlan,
        autoInputRunner: (Context, Context, SmsMsg, Boolean) -> Unit = ::runAutoInputNow,
        recordRunner: (Context, Context, SmsMsg, String, Boolean) -> Unit = ::runRecordNow,
    ) {
        SharedSmsCodeActionDispatcher.dispatchObservedSmsActions(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            plan = plan.toShared(),
            autoInputRunner = autoInputRunner,
            recordRunner = recordRunner,
        )
    }

    private fun dispatchUiActions(
        uiHandler: Handler,
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        uiPlan: SmsCodePostParseCoordinator.UiPlan,
        autoInputDelayMs: Long?,
    ) {
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
        val toastDelayMs = resolveToastDelayMs(autoInputDelayMs)
        if (toastDelayMs <= 0L) {
            uiHandler.post(toastAction)
        } else {
            executor.schedule(
                { uiHandler.post(toastAction) },
                toastDelayMs,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun resolveToastDelayMs(autoInputDelayMs: Long?): Long {
        if (autoInputDelayMs == null) return 0L
        return autoInputDelayMs + TOAST_AFTER_AUTO_INPUT_BUFFER_MS
    }

    fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        deduplicateEnabled: Boolean,
    ) {
        AutoInputAction(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            deduplicateEnabled = deduplicateEnabled,
        ).call()
    }

    fun scheduleAutoInput(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
        deduplicateEnabled: Boolean,
    ) {
        executor.schedule(
            AutoInputAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                deduplicateEnabled = deduplicateEnabled,
            ),
            delayMs,
            TimeUnit.MILLISECONDS,
        )
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
        executor.schedule(
            RecordSmsAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                eventId = eventId,
                enabled = true,
                deduplicateEnabled = deduplicateEnabled,
            ),
            0,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun scheduleNotification(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        plan: SmsCodePostParseCoordinator.NotificationPlan,
    ) {
        executor.schedule(
            NotifyAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = true,
                autoCancelEnabled = plan.autoCancelDelayMs != null,
                retentionTimeMs = plan.autoCancelDelayMs ?: 0L,
            ),
            0,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun scheduleOperateSmsActions(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delays: List<Long>,
    ) {
        delays.forEach { delayMs ->
            executor.schedule(
                OperateSmsAction(pluginContext, phoneContext, smsMsg),
                delayMs,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun SmsCodePostParseCoordinator.ParsedSmsPlan.toShared(): SharedSmsCodePostParseCoordinator.ParsedSmsPlan {
        return SharedSmsCodePostParseCoordinator.ParsedSmsPlan(
            blockSms = blockSms,
            deduplicateSmsEnabled = deduplicateSmsEnabled,
            uiPlan = uiPlan.toShared(),
            autoInputDelayMs = autoInputDelayMs,
            notificationPlan = notificationPlan?.toShared(),
            shouldRecord = shouldRecord,
            operateSmsDelays = operateSmsDelays,
        )
    }

    private fun SmsCodePostParseCoordinator.ObservedSmsPlan.toShared(): SharedSmsCodePostParseCoordinator.ObservedSmsPlan {
        return SharedSmsCodePostParseCoordinator.ObservedSmsPlan(
            deduplicateSmsEnabled = deduplicateSmsEnabled,
            autoInputEnabled = autoInputEnabled,
            shouldRecord = shouldRecord,
        )
    }

    private fun SmsCodePostParseCoordinator.UiPlan.toShared(): SharedSmsCodePostParseCoordinator.UiPlan {
        return SharedSmsCodePostParseCoordinator.UiPlan(
            copyToClipboardEnabled = copyToClipboardEnabled,
            showToast = showToast,
        )
    }

    private fun SmsCodePostParseCoordinator.NotificationPlan.toShared(): SharedSmsCodePostParseCoordinator.NotificationPlan {
        return SharedSmsCodePostParseCoordinator.NotificationPlan(
            autoCancelDelayMs = autoCancelDelayMs,
        )
    }

    private fun SharedSmsCodePostParseCoordinator.UiPlan.toLocal(): SmsCodePostParseCoordinator.UiPlan {
        return SmsCodePostParseCoordinator.UiPlan(
            copyToClipboardEnabled = copyToClipboardEnabled,
            showToast = showToast,
        )
    }

    private fun SharedSmsCodePostParseCoordinator.NotificationPlan.toLocal(): SmsCodePostParseCoordinator.NotificationPlan {
        return SmsCodePostParseCoordinator.NotificationPlan(
            autoCancelDelayMs = autoCancelDelayMs,
        )
    }

    private const val TOAST_AFTER_AUTO_INPUT_BUFFER_MS = 250L
}
