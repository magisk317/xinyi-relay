package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xp.XpPrefs

internal object SmsCodePlanFactory {
    fun loadSettings(pluginContext: Context): SmsCodePostParseCoordinator.Settings {
        return SmsCodePostParseCoordinator.Settings(
            showNotification = XpPrefs.showCodeNotification(pluginContext),
            autoCancelNotification = XpPrefs.autoCancelCodeNotification(pluginContext),
            notificationRetentionMs = XpPrefs.getNotificationRetentionTime(pluginContext) * 1000L,
            autoInputEnabled = XpPrefs.autoInputCodeEnabled(pluginContext),
            autoInputDelayMs = XpPrefs.getAutoInputCodeDelay(pluginContext) * 1000L,
            copyToClipboardEnabled = XpPrefs.copyToClipboardEnabled(pluginContext),
            showToast = XpPrefs.shouldShowToast(pluginContext),
            recordSmsEnabled = XpPrefs.recordSmsCodeEnabled(pluginContext),
            blockSmsEnabled = XpPrefs.blockSmsEnabled(pluginContext),
            markAsReadEnabled = XpPrefs.markAsReadEnabled(pluginContext),
            deleteSmsEnabled = XpPrefs.deleteSmsEnabled(pluginContext),
            deduplicateSmsEnabled = XpPrefs.deduplicateSms(pluginContext),
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
