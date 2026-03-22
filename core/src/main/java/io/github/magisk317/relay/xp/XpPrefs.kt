package io.github.magisk317.relay.xp

import android.content.Context
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.xp.XpRuntimeBridge

object XpPrefs {
    fun installRuntimeBridge(bridge: XpRuntimeBridge?) {
        PrefsReader.installRuntimeBridge(bridge)
    }

    fun isEnabled(context: Context): Boolean = PrefsReader.isEnabled(context)

    fun isVerboseLogMode(context: Context): Boolean = PrefsReader.isVerboseLogMode(context)

    fun relayFeaturesEnabled(context: Context): Boolean = PrefsReader.relayFeaturesEnabled(context)

    fun autoInputCodeEnabled(context: Context): Boolean = PrefsReader.autoInputCodeEnabled(context)

    fun autoEnterCodeEnabled(context: Context): Boolean = PrefsReader.autoEnterCodeEnabled(context)

    fun getAutoInputCodeDelay(context: Context): Long = PrefsReader.getAutoInputCodeDelay(context)

    fun getAutoInputCodeIntervalMs(context: Context): Long = PrefsReader.getAutoInputCodeIntervalMs(context)

    fun shouldShowToast(context: Context): Boolean = PrefsReader.shouldShowToast(context)

    fun markAsReadEnabled(context: Context): Boolean = PrefsReader.markAsReadEnabled(context)

    fun deleteSmsEnabled(context: Context): Boolean = PrefsReader.deleteSmsEnabled(context)

    fun copyToClipboardEnabled(context: Context): Boolean = PrefsReader.copyToClipboardEnabled(context)

    fun isMessageTypeEnabled(context: Context, messageType: MessageType): Boolean {
        return PrefsReader.isMessageTypeEnabled(context, messageType)
    }

    fun recordSmsCodeEnabled(context: Context): Boolean = PrefsReader.recordSmsCodeEnabled(context)

    fun blockSmsEnabled(context: Context): Boolean = PrefsReader.blockSmsEnabled(context)

    fun showCodeNotification(context: Context): Boolean = PrefsReader.showCodeNotification(context)

    fun autoCancelCodeNotification(context: Context): Boolean = PrefsReader.autoCancelCodeNotification(context)

    fun getNotificationRetentionTime(context: Context): Int = PrefsReader.getNotificationRetentionTime(context)

    fun deduplicateSms(context: Context): Boolean = PrefsReader.deduplicateSms(context)

    fun getIpcToken(context: Context): String = PrefsReader.getIpcToken(context)
}
