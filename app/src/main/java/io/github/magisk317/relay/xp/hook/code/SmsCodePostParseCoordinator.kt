package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.impl.AutoInputAction
import io.github.magisk317.relay.xp.hook.code.action.impl.ForwardAction
import io.github.magisk317.relay.xp.hook.code.action.impl.RecordSmsAction
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

    fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
    ) {
        AutoInputAction(pluginContext, phoneContext, smsMsg).call()
    }

    fun scheduleAutoInput(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
    ) {
        executor.schedule(
            AutoInputAction(pluginContext, phoneContext, smsMsg),
            delayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    fun runRecordNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
    ) {
        RecordSmsAction(pluginContext, phoneContext, smsMsg, eventId).call()
    }

    fun scheduleRecord(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
    ) {
        executor.schedule(
            RecordSmsAction(pluginContext, phoneContext, smsMsg, eventId),
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

    private val MARK_AS_READ_RETRY_DELAYS_MS = listOf(300L, 1000L, 2000L)
    private val DELETE_SMS_DELAYS_MS = listOf(300L)
}
