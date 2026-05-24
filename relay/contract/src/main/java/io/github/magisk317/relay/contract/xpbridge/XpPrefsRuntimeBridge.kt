package io.github.magisk317.relay.contract.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.prefs.XpRuntimeBridge

interface XpPrefsRuntimeBridge {
    fun installRuntimeBridge(bridge: XpRuntimeBridge?)
    fun isEnabled(context: Context): Boolean
    fun isVerboseLogMode(context: Context): Boolean
    fun isSensitiveDebugLogSupported(): Boolean
    fun isSensitiveDebugLogMode(context: Context): Boolean
    fun relayFeaturesEnabled(context: Context): Boolean
    fun autoInputCodeEnabled(context: Context): Boolean
    fun autoEnterCodeEnabled(context: Context): Boolean
    fun getAutoInputCodeDelay(context: Context): Long
    fun getAutoInputCodeIntervalMs(context: Context): Long
    fun shouldShowToast(context: Context): Boolean
    fun markAsReadEnabled(context: Context): Boolean
    fun deleteSmsEnabled(context: Context): Boolean
    fun copyToClipboardEnabled(context: Context): Boolean
    fun isMessageTypeEnabled(context: Context, messageType: XpMessageType): Boolean
    fun recordSmsCodeEnabled(context: Context): Boolean
    fun blockSmsEnabled(context: Context): Boolean
    fun showCodeNotification(context: Context): Boolean
    fun getCodeNotificationOwner(context: Context): String
    fun autoCancelCodeNotification(context: Context): Boolean
    fun getNotificationRetentionTime(context: Context): Int
    fun deduplicateSms(context: Context): Boolean
    fun getIpcToken(context: Context): String
}

object NoopXpPrefsRuntimeBridge : XpPrefsRuntimeBridge {
    override fun installRuntimeBridge(bridge: XpRuntimeBridge?) = Unit
    override fun isEnabled(context: Context): Boolean = true
    override fun isVerboseLogMode(context: Context): Boolean = false
    override fun isSensitiveDebugLogSupported(): Boolean = false
    override fun isSensitiveDebugLogMode(context: Context): Boolean = false
    override fun relayFeaturesEnabled(context: Context): Boolean = true
    override fun autoInputCodeEnabled(context: Context): Boolean = true
    override fun autoEnterCodeEnabled(context: Context): Boolean = false
    override fun getAutoInputCodeDelay(context: Context): Long = PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
    override fun getAutoInputCodeIntervalMs(context: Context): Long =
        PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT.toLong()
    override fun shouldShowToast(context: Context): Boolean = true
    override fun markAsReadEnabled(context: Context): Boolean = false
    override fun deleteSmsEnabled(context: Context): Boolean = false
    override fun copyToClipboardEnabled(context: Context): Boolean = false
    override fun isMessageTypeEnabled(context: Context, messageType: XpMessageType): Boolean {
        return messageType != XpMessageType.CALL_NOTIFY
    }
    override fun recordSmsCodeEnabled(context: Context): Boolean = true
    override fun blockSmsEnabled(context: Context): Boolean = false
    override fun showCodeNotification(context: Context): Boolean = true
    override fun getCodeNotificationOwner(context: Context): String = ""
    override fun autoCancelCodeNotification(context: Context): Boolean = false
    override fun getNotificationRetentionTime(context: Context): Int =
        PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT.toInt()
    override fun deduplicateSms(context: Context): Boolean = true
    override fun getIpcToken(context: Context): String = ""
}
