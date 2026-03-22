package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import android.os.Handler
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.data.db.entity.SmsMsg
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

internal object SmsCodePostParseCoordinator {
    data class Settings(
        val showNotification: Boolean,
        val autoCancelNotification: Boolean,
        val notificationRetentionMs: Long,
        val autoInputEnabled: Boolean,
        val autoInputDelayMs: Long,
        val copyToClipboardEnabled: Boolean,
        val showToast: Boolean,
        val recordSmsEnabled: Boolean,
        val blockSmsEnabled: Boolean,
        val markAsReadEnabled: Boolean,
        val deleteSmsEnabled: Boolean,
        val deduplicateSmsEnabled: Boolean,
    )

    data class UiPlan(
        val copyToClipboardEnabled: Boolean,
        val showToast: Boolean,
    )

    data class NotificationPlan(
        val autoCancelDelayMs: Long?,
    )

    data class ParsedSmsPlan(
        val blockSms: Boolean,
        val deduplicateSmsEnabled: Boolean,
        val uiPlan: UiPlan,
        val autoInputDelayMs: Long?,
        val notificationPlan: NotificationPlan?,
        val shouldRecord: Boolean,
        val forwardDelayMs: Long,
        val operateSmsDelays: List<Long>,
    )

    data class ObservedSmsPlan(
        val deduplicateSmsEnabled: Boolean,
        val autoInputEnabled: Boolean,
        val shouldRecord: Boolean,
    )

    fun loadSettings(pluginContext: Context): Settings {
        return Settings(
            showNotification = PrefsReader.showCodeNotification(pluginContext),
            autoCancelNotification = PrefsReader.autoCancelCodeNotification(pluginContext),
            notificationRetentionMs = PrefsReader.getNotificationRetentionTime(pluginContext) * 1000L,
            autoInputEnabled = PrefsReader.autoInputCodeEnabled(pluginContext),
            autoInputDelayMs = PrefsReader.getAutoInputCodeDelay(pluginContext) * 1000L,
            copyToClipboardEnabled = PrefsReader.copyToClipboardEnabled(pluginContext),
            showToast = PrefsReader.shouldShowToast(pluginContext),
            recordSmsEnabled = PrefsReader.recordSmsCodeEnabled(pluginContext),
            blockSmsEnabled = PrefsReader.blockSmsEnabled(pluginContext),
            markAsReadEnabled = PrefsReader.markAsReadEnabled(pluginContext),
            deleteSmsEnabled = PrefsReader.deleteSmsEnabled(pluginContext),
            deduplicateSmsEnabled = PrefsReader.deduplicateSms(pluginContext),
        )
    }

    fun resolveOperateSmsDelays(settings: Settings): List<Long> {
        return when {
            settings.deleteSmsEnabled -> DELETE_SMS_DELAYS_MS
            settings.markAsReadEnabled -> MARK_AS_READ_RETRY_DELAYS_MS
            else -> emptyList()
        }
    }

    fun createParsedSmsPlan(
        settings: Settings,
        forwardDelayMs: Long,
    ): ParsedSmsPlan {
        return ParsedSmsPlan(
            blockSms = settings.blockSmsEnabled,
            deduplicateSmsEnabled = settings.deduplicateSmsEnabled,
            uiPlan = UiPlan(
                copyToClipboardEnabled = settings.copyToClipboardEnabled,
                showToast = settings.showToast,
            ),
            autoInputDelayMs = if (settings.autoInputEnabled) settings.autoInputDelayMs else null,
            notificationPlan = if (settings.showNotification) {
                NotificationPlan(
                    autoCancelDelayMs = if (settings.autoCancelNotification) settings.notificationRetentionMs else null,
                )
            } else {
                null
            },
            shouldRecord = settings.recordSmsEnabled,
            forwardDelayMs = forwardDelayMs,
            operateSmsDelays = resolveOperateSmsDelays(settings),
        )
    }

    fun createObservedSmsPlan(settings: Settings): ObservedSmsPlan {
        return ObservedSmsPlan(
            deduplicateSmsEnabled = settings.deduplicateSmsEnabled,
            autoInputEnabled = settings.autoInputEnabled,
            shouldRecord = settings.recordSmsEnabled && !settings.deduplicateSmsEnabled,
        )
    }

    fun dispatchParsedSmsActions(
        uiHandler: Handler,
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        smsIntent: Intent?,
        eventId: String,
        plan: ParsedSmsPlan,
    ) {
        dispatchUiActions(
            uiHandler = uiHandler,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            uiPlan = plan.uiPlan,
        )

        plan.autoInputDelayMs?.let { delayMs ->
            scheduleAutoInput(
                executor = executor,
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                delayMs = delayMs,
                deduplicateEnabled = plan.deduplicateSmsEnabled,
            )
        }

        plan.notificationPlan?.let { notificationPlan ->
            scheduleNotification(
                executor = executor,
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                plan = notificationPlan,
            )
        }

        if (plan.shouldRecord) {
            scheduleRecord(
                executor = executor,
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                eventId = eventId,
                deduplicateEnabled = plan.deduplicateSmsEnabled,
            )
        }

        scheduleForward(
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            smsIntent = smsIntent,
            eventId = eventId,
            delayMs = plan.forwardDelayMs,
        )

        scheduleOperateSmsActions(
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            delays = plan.operateSmsDelays,
        )
    }

    fun dispatchObservedSmsActions(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: ObservedSmsPlan,
    ) {
        if (plan.autoInputEnabled) {
            runAutoInputNow(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                deduplicateEnabled = plan.deduplicateSmsEnabled,
            )
        }
        if (plan.shouldRecord) {
            runRecordNow(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                eventId = eventId,
                deduplicateEnabled = false,
            )
        }
    }

    private fun dispatchUiActions(
        uiHandler: Handler,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        uiPlan: UiPlan,
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
        plan: NotificationPlan,
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

    private val MARK_AS_READ_RETRY_DELAYS_MS = listOf(300L, 1000L, 2000L)
    private val DELETE_SMS_DELAYS_MS = listOf(300L)
}
