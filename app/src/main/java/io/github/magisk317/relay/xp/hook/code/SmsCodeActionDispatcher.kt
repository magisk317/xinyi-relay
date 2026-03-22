package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import android.os.Handler
import io.github.magisk317.relay.xp.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.impl.AutoInputAction
import io.github.magisk317.relay.xp.hook.code.action.impl.CancelNotifyAction
import io.github.magisk317.relay.xp.hook.code.action.impl.CopyToClipboardAction
import io.github.magisk317.relay.xp.hook.code.action.impl.ForwardAction
import io.github.magisk317.relay.xp.hook.code.action.impl.NotifyAction
import io.github.magisk317.relay.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.relay.xp.hook.code.action.impl.RecordSmsAction
import io.github.magisk317.relay.xp.hook.code.action.impl.ToastAction
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal object SmsCodeActionDispatcher {
    fun dispatchParsedSmsActions(
        uiHandler: Handler,
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        smsIntent: Intent?,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ParsedSmsPlan,
        uiDispatcher: (
            Handler,
            Context,
            Context,
            SmsMsg,
            SmsCodePostParseCoordinator.UiPlan,
        ) -> Unit = ::dispatchUiActions,
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
        forwardScheduler: (
            ScheduledExecutorService,
            Context,
            Context,
            SmsMsg,
            Intent?,
            String,
            Long,
        ) -> Unit = ::scheduleForward,
        operateSmsScheduler: (
            ScheduledExecutorService,
            Context,
            Context,
            SmsMsg,
            List<Long>,
        ) -> Unit = ::scheduleOperateSmsActions,
    ) {
        uiDispatcher(
            uiHandler,
            pluginContext,
            phoneContext,
            smsMsg,
            plan.uiPlan,
        )

        plan.autoInputDelayMs?.let { delayMs ->
            autoInputScheduler(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                delayMs,
                plan.deduplicateSmsEnabled,
            )
        }

        plan.notificationPlan?.let { notificationPlan ->
            notificationScheduler(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                notificationPlan,
            )
        }

        if (plan.shouldRecord) {
            recordScheduler(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                eventId,
                plan.deduplicateSmsEnabled,
            )
        }

        forwardScheduler(
            executor,
            pluginContext,
            phoneContext,
            smsMsg,
            smsIntent,
            eventId,
            plan.forwardDelayMs,
        )

        operateSmsScheduler(
            executor,
            pluginContext,
            phoneContext,
            smsMsg,
            plan.operateSmsDelays,
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
        if (plan.autoInputEnabled) {
            autoInputRunner(
                pluginContext,
                phoneContext,
                smsMsg,
                plan.deduplicateSmsEnabled,
            )
        }
        if (plan.shouldRecord) {
            recordRunner(
                pluginContext,
                phoneContext,
                smsMsg,
                eventId,
                false,
            )
        }
    }

    private fun dispatchUiActions(
        uiHandler: Handler,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        uiPlan: SmsCodePostParseCoordinator.UiPlan,
    ) {
        uiHandler.post(
            CopyToClipboardAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = uiPlan.copyToClipboardEnabled,
            ),
        )
        uiHandler.post(
            ToastAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = uiPlan.showToast,
            ),
        )
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

    fun scheduleForward(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        smsIntent: Intent?,
        eventId: String,
        delayMs: Long,
    ) {
        executor.schedule(
            ForwardAction(
                pluginContext,
                phoneContext,
                smsMsg,
                smsIntent,
                eventId,
            ),
            delayMs,
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

        val autoCancelDelayMs = plan.autoCancelDelayMs ?: return
        val cancelNotifyAction = CancelNotifyAction(pluginContext, phoneContext, smsMsg).apply {
            setNotificationId(smsMsg.hashCode())
        }
        executor.schedule(cancelNotifyAction, autoCancelDelayMs, TimeUnit.MILLISECONDS)
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
}
