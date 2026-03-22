package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import android.os.Handler
import io.github.magisk317.relay.data.db.entity.SmsMsg
import java.util.concurrent.ScheduledExecutorService

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
        return SmsCodePlanFactory.loadSettings(pluginContext)
    }

    fun resolveOperateSmsDelays(settings: Settings): List<Long> {
        return SmsCodePlanFactory.resolveOperateSmsDelays(settings)
    }

    fun createParsedSmsPlan(
        settings: Settings,
        forwardDelayMs: Long,
    ): ParsedSmsPlan {
        return SmsCodePlanFactory.createParsedSmsPlan(settings, forwardDelayMs)
    }

    fun createObservedSmsPlan(settings: Settings): ObservedSmsPlan {
        return SmsCodePlanFactory.createObservedSmsPlan(settings)
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
        SmsCodeActionDispatcher.dispatchParsedSmsActions(
            uiHandler = uiHandler,
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            smsIntent = smsIntent,
            eventId = eventId,
            plan = plan,
        )
    }

    fun dispatchObservedSmsActions(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: ObservedSmsPlan,
    ) {
        SmsCodeActionDispatcher.dispatchObservedSmsActions(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            plan = plan,
        )
    }

    fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        deduplicateEnabled: Boolean,
    ) {
        SmsCodeActionDispatcher.runAutoInputNow(pluginContext, phoneContext, smsMsg, deduplicateEnabled)
    }

    fun scheduleAutoInput(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
        deduplicateEnabled: Boolean,
    ) {
        SmsCodeActionDispatcher.scheduleAutoInput(
            executor,
            pluginContext,
            phoneContext,
            smsMsg,
            delayMs,
            deduplicateEnabled,
        )
    }

    fun runRecordNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        SmsCodeActionDispatcher.runRecordNow(pluginContext, phoneContext, smsMsg, eventId, deduplicateEnabled)
    }

    fun scheduleRecord(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        SmsCodeActionDispatcher.scheduleRecord(
            executor,
            pluginContext,
            phoneContext,
            smsMsg,
            eventId,
            deduplicateEnabled,
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
        SmsCodeActionDispatcher.scheduleForward(
            executor,
            pluginContext,
            phoneContext,
            smsMsg,
            smsIntent,
            eventId,
            delayMs,
        )
    }
}
