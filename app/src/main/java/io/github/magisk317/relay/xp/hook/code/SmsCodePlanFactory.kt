package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.common.utils.PrefsReader

internal object SmsCodePlanFactory {
    fun loadSettings(pluginContext: Context): SmsCodePostParseCoordinator.Settings {
        return SmsCodePostParseCoordinator.Settings(
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

    fun resolveOperateSmsDelays(
        settings: SmsCodePostParseCoordinator.Settings,
    ): List<Long> {
        return when {
            settings.deleteSmsEnabled -> DELETE_SMS_DELAYS_MS
            settings.markAsReadEnabled -> MARK_AS_READ_RETRY_DELAYS_MS
            else -> emptyList()
        }
    }

    fun createParsedSmsPlan(
        settings: SmsCodePostParseCoordinator.Settings,
        forwardDelayMs: Long,
    ): SmsCodePostParseCoordinator.ParsedSmsPlan {
        return SmsCodePostParseCoordinator.ParsedSmsPlan(
            blockSms = settings.blockSmsEnabled,
            deduplicateSmsEnabled = settings.deduplicateSmsEnabled,
            uiPlan = SmsCodePostParseCoordinator.UiPlan(
                copyToClipboardEnabled = settings.copyToClipboardEnabled,
                showToast = settings.showToast,
            ),
            autoInputDelayMs = if (settings.autoInputEnabled) settings.autoInputDelayMs else null,
            notificationPlan = if (settings.showNotification) {
                SmsCodePostParseCoordinator.NotificationPlan(
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

    fun createObservedSmsPlan(
        settings: SmsCodePostParseCoordinator.Settings,
    ): SmsCodePostParseCoordinator.ObservedSmsPlan {
        return SmsCodePostParseCoordinator.ObservedSmsPlan(
            deduplicateSmsEnabled = settings.deduplicateSmsEnabled,
            autoInputEnabled = settings.autoInputEnabled,
            shouldRecord = settings.recordSmsEnabled && !settings.deduplicateSmsEnabled,
        )
    }

    private val MARK_AS_READ_RETRY_DELAYS_MS = listOf(300L, 1000L, 2000L)
    private val DELETE_SMS_DELAYS_MS = listOf(300L)
}
