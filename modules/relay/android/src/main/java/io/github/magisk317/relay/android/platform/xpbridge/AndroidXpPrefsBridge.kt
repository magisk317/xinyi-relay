package io.github.magisk317.relay.android.platform.xpbridge

import android.content.Context
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.contract.prefs.XpRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpMessageType
import io.github.magisk317.relay.contract.xpbridge.XpPrefsRuntimeBridge

object AndroidXpPrefsBridge : XpPrefsRuntimeBridge {
    override fun installRuntimeBridge(bridge: XpRuntimeBridge?) {
        PrefsReader.installRuntimeBridge(bridge)
    }

    override fun isEnabled(context: Context): Boolean = PrefsReader.isEnabled(context)

    override fun isVerboseLogMode(context: Context): Boolean = PrefsReader.isVerboseLogMode(context)

    override fun isSensitiveDebugLogSupported(): Boolean = PrefsReader.isSensitiveDebugLogSupported()

    override fun isSensitiveDebugLogMode(context: Context): Boolean = PrefsReader.isSensitiveDebugLogMode(context)

    override fun relayFeaturesEnabled(context: Context): Boolean = PrefsReader.relayFeaturesEnabled(context)

    override fun autoInputCodeEnabled(context: Context): Boolean = PrefsReader.autoInputCodeEnabled(context)

    override fun autoEnterCodeEnabled(context: Context): Boolean = PrefsReader.autoEnterCodeEnabled(context)

    override fun getAutoInputCodeDelay(context: Context): Long = PrefsReader.getAutoInputCodeDelay(context)

    override fun getAutoInputCodeIntervalMs(context: Context): Long = PrefsReader.getAutoInputCodeIntervalMs(context)

    override fun shouldShowToast(context: Context): Boolean = PrefsReader.shouldShowToast(context)

    override fun markAsReadEnabled(context: Context): Boolean = PrefsReader.markAsReadEnabled(context)

    override fun deleteSmsEnabled(context: Context): Boolean = PrefsReader.deleteSmsEnabled(context)

    override fun copyToClipboardEnabled(context: Context): Boolean = PrefsReader.copyToClipboardEnabled(context)

    override fun isMessageTypeEnabled(context: Context, messageType: XpMessageType): Boolean {
        return PrefsReader.isMessageTypeEnabled(context, messageType.toRuntimeMessageType())
    }

    override fun recordSmsCodeEnabled(context: Context): Boolean = PrefsReader.recordSmsCodeEnabled(context)

    override fun blockSmsEnabled(context: Context): Boolean = PrefsReader.blockSmsEnabled(context)

    override fun showCodeNotification(context: Context): Boolean = PrefsReader.showCodeNotification(context)

    override fun getCodeNotificationOwner(context: Context): String = PrefsReader.getCodeNotificationOwner(context)

    override fun autoCancelCodeNotification(context: Context): Boolean = PrefsReader.autoCancelCodeNotification(context)

    override fun getNotificationRetentionTime(context: Context): Int = PrefsReader.getNotificationRetentionTime(context)

    override fun deduplicateSms(context: Context): Boolean = PrefsReader.deduplicateSms(context)

    override fun getIpcToken(context: Context): String = PrefsReader.getIpcToken(context)

    private fun XpMessageType.toRuntimeMessageType(): MessageType {
        return when (this) {
            XpMessageType.SMS_CODE -> MessageType.SMS_CODE
            XpMessageType.SMS_PLAIN -> MessageType.SMS_PLAIN
            XpMessageType.APP_NOTIFY -> MessageType.APP_NOTIFY
            XpMessageType.CALL_NOTIFY -> MessageType.CALL_NOTIFY
        }
    }
}
