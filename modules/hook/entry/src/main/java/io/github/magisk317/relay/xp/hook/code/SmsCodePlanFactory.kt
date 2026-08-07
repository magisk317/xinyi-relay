package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.verification.SmsCodePlanFactory as SharedSmsCodePlanFactory
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.verification.VerificationPrefs

internal object SmsCodePlanFactory {
    fun loadSettings(pluginContext: Context): SmsCodePostParseCoordinator.Settings {
        return SharedSmsCodePlanFactory.loadSettings(RelayVerificationPrefs(pluginContext))
    }

    fun resolveOperateSmsDelays(
        settings: SmsCodePostParseCoordinator.Settings,
    ): List<Long> {
        return SharedSmsCodePlanFactory.resolveOperateSmsDelays(settings)
    }

    fun createParsedSmsPlan(
        settings: SmsCodePostParseCoordinator.Settings,
    ): SmsCodePostParseCoordinator.ParsedSmsPlan {
        return SharedSmsCodePlanFactory.createParsedSmsPlan(settings)
    }

    fun createObservedSmsPlan(
        settings: SmsCodePostParseCoordinator.Settings,
    ): SmsCodePostParseCoordinator.ObservedSmsPlan {
        return SharedSmsCodePlanFactory.createObservedSmsPlan(settings)
    }
}

private class RelayVerificationPrefs(
    private val context: Context,
) : VerificationPrefs {
    private fun mobileAutomationAllowed(): Boolean = XpPrefs.mobileAutomationAllowed(context)

    override fun showNotification(): Boolean = mobileAutomationAllowed() && XpPrefs.showCodeNotification(context)
    override fun autoCancelNotification(): Boolean = mobileAutomationAllowed() && XpPrefs.autoCancelCodeNotification(context)
    override fun notificationRetentionMs(): Long = XpPrefs.getNotificationRetentionTime(context) * 1000L
    override fun autoInputEnabled(): Boolean = mobileAutomationAllowed() && XpPrefs.autoInputCodeEnabled(context)
    override fun autoInputDelayMs(): Long = XpPrefs.getAutoInputCodeDelay(context) * 1000L
    override fun copyToClipboardEnabled(): Boolean = mobileAutomationAllowed() && XpPrefs.copyToClipboardEnabled(context)
    override fun showToast(): Boolean = mobileAutomationAllowed() && XpPrefs.shouldShowToast(context)
    override fun recordSmsEnabled(): Boolean = XpPrefs.recordSmsCodeEnabled(context)
    override fun blockSmsEnabled(): Boolean = mobileAutomationAllowed() && XpPrefs.blockSmsEnabled(context)
    override fun markAsReadEnabled(): Boolean = mobileAutomationAllowed() && XpPrefs.markAsReadEnabled(context)
    override fun deleteSmsEnabled(): Boolean = mobileAutomationAllowed() && XpPrefs.deleteSmsEnabled(context)
    override fun deduplicateSmsEnabled(): Boolean = XpPrefs.deduplicateSms(context)
}
