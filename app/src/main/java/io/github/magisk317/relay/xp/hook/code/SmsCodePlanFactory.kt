package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.verification.SmsCodePlanFactory as SharedSmsCodePlanFactory
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator as SharedSmsCodePostParseCoordinator
import io.github.magisk317.smscode.verification.VerificationPrefs

internal object SmsCodePlanFactory {
    fun loadSettings(pluginContext: Context): SmsCodePostParseCoordinator.Settings {
        return SharedSmsCodePlanFactory
            .loadSettings(RelayVerificationPrefs(pluginContext))
            .toLocal()
    }

    fun resolveOperateSmsDelays(
        settings: SmsCodePostParseCoordinator.Settings,
    ): List<Long> {
        return SharedSmsCodePlanFactory.resolveOperateSmsDelays(settings.toShared())
    }

    fun createParsedSmsPlan(
        settings: SmsCodePostParseCoordinator.Settings,
    ): SmsCodePostParseCoordinator.ParsedSmsPlan {
        return SharedSmsCodePlanFactory.createParsedSmsPlan(settings.toShared()).toLocal()
    }

    fun createObservedSmsPlan(
        settings: SmsCodePostParseCoordinator.Settings,
    ): SmsCodePostParseCoordinator.ObservedSmsPlan {
        return SharedSmsCodePlanFactory.createObservedSmsPlan(settings.toShared()).toLocal()
    }
}

private class RelayVerificationPrefs(
    private val context: Context,
) : VerificationPrefs {
    override fun showNotification(): Boolean = XpPrefs.showCodeNotification(context)
    override fun autoCancelNotification(): Boolean = XpPrefs.autoCancelCodeNotification(context)
    override fun notificationRetentionMs(): Long = XpPrefs.getNotificationRetentionTime(context) * 1000L
    override fun autoInputEnabled(): Boolean = XpPrefs.autoInputCodeEnabled(context)
    override fun autoInputDelayMs(): Long = XpPrefs.getAutoInputCodeDelay(context) * 1000L
    override fun copyToClipboardEnabled(): Boolean = XpPrefs.copyToClipboardEnabled(context)
    override fun showToast(): Boolean = XpPrefs.shouldShowToast(context)
    override fun recordSmsEnabled(): Boolean = XpPrefs.recordSmsCodeEnabled(context)
    override fun blockSmsEnabled(): Boolean = XpPrefs.blockSmsEnabled(context)
    override fun markAsReadEnabled(): Boolean = XpPrefs.markAsReadEnabled(context)
    override fun deleteSmsEnabled(): Boolean = XpPrefs.deleteSmsEnabled(context)
    override fun deduplicateSmsEnabled(): Boolean = XpPrefs.deduplicateSms(context)
}

private fun SharedSmsCodePostParseCoordinator.Settings.toLocal(): SmsCodePostParseCoordinator.Settings {
    return SmsCodePostParseCoordinator.Settings(
        showNotification = showNotification,
        autoCancelNotification = autoCancelNotification,
        notificationRetentionMs = notificationRetentionMs,
        autoInputEnabled = autoInputEnabled,
        autoInputDelayMs = autoInputDelayMs,
        copyToClipboardEnabled = copyToClipboardEnabled,
        showToast = showToast,
        recordSmsEnabled = recordSmsEnabled,
        blockSmsEnabled = blockSmsEnabled,
        markAsReadEnabled = markAsReadEnabled,
        deleteSmsEnabled = deleteSmsEnabled,
        deduplicateSmsEnabled = deduplicateSmsEnabled,
    )
}

private fun SmsCodePostParseCoordinator.Settings.toShared(): SharedSmsCodePostParseCoordinator.Settings {
    return SharedSmsCodePostParseCoordinator.Settings(
        showNotification = showNotification,
        autoCancelNotification = autoCancelNotification,
        notificationRetentionMs = notificationRetentionMs,
        autoInputEnabled = autoInputEnabled,
        autoInputDelayMs = autoInputDelayMs,
        copyToClipboardEnabled = copyToClipboardEnabled,
        showToast = showToast,
        recordSmsEnabled = recordSmsEnabled,
        blockSmsEnabled = blockSmsEnabled,
        markAsReadEnabled = markAsReadEnabled,
        deleteSmsEnabled = deleteSmsEnabled,
        deduplicateSmsEnabled = deduplicateSmsEnabled,
    )
}

private fun SharedSmsCodePostParseCoordinator.ParsedSmsPlan.toLocal(): SmsCodePostParseCoordinator.ParsedSmsPlan {
    return SmsCodePostParseCoordinator.ParsedSmsPlan(
        blockSms = blockSms,
        deduplicateSmsEnabled = deduplicateSmsEnabled,
        uiPlan = SmsCodePostParseCoordinator.UiPlan(
            copyToClipboardEnabled = uiPlan.copyToClipboardEnabled,
            showToast = uiPlan.showToast,
        ),
        autoInputDelayMs = autoInputDelayMs,
        notificationPlan = notificationPlan?.let {
            SmsCodePostParseCoordinator.NotificationPlan(autoCancelDelayMs = it.autoCancelDelayMs)
        },
        shouldRecord = shouldRecord,
        operateSmsDelays = operateSmsDelays,
    )
}

private fun SharedSmsCodePostParseCoordinator.ObservedSmsPlan.toLocal(): SmsCodePostParseCoordinator.ObservedSmsPlan {
    return SmsCodePostParseCoordinator.ObservedSmsPlan(
        deduplicateSmsEnabled = deduplicateSmsEnabled,
        autoInputEnabled = autoInputEnabled,
        shouldRecord = shouldRecord,
    )
}
