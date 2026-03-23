package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.prefs.PrefsReader
import io.github.magisk317.relay.xpbridge.bridge.NoopXpRuntimeBridge
import io.github.magisk317.relay.xpbridge.bridge.PrefReadResult as CorePrefReadResult
import io.github.magisk317.relay.xpbridge.bridge.PrefsSource as CorePrefsSource
import io.github.magisk317.relay.xpbridge.bridge.XpCapabilities as CoreXpCapabilities
import io.github.magisk317.relay.xpbridge.bridge.XpRuntimeBridge as CoreXpRuntimeBridge
import io.github.magisk317.relay.prefs.bridge.PrefReadResult as RuntimePrefReadResult
import io.github.magisk317.relay.prefs.bridge.PrefsSource as RuntimePrefsSource
import io.github.magisk317.relay.prefs.bridge.XpCapabilities as RuntimeXpCapabilities
import io.github.magisk317.relay.prefs.bridge.XpRuntimeBridge as RuntimeXpRuntimeBridge

object XpPrefs {
    fun installRuntimeBridge(bridge: CoreXpRuntimeBridge?) {
        PrefsReader.installRuntimeBridge((bridge ?: NoopXpRuntimeBridge).toRuntimeBridge())
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

    fun isMessageTypeEnabled(context: Context, messageType: XpMessageType): Boolean {
        return PrefsReader.isMessageTypeEnabled(context, messageType.toRuntimeMessageType())
    }

    fun recordSmsCodeEnabled(context: Context): Boolean = PrefsReader.recordSmsCodeEnabled(context)

    fun blockSmsEnabled(context: Context): Boolean = PrefsReader.blockSmsEnabled(context)

    fun showCodeNotification(context: Context): Boolean = PrefsReader.showCodeNotification(context)

    fun autoCancelCodeNotification(context: Context): Boolean = PrefsReader.autoCancelCodeNotification(context)

    fun getNotificationRetentionTime(context: Context): Int = PrefsReader.getNotificationRetentionTime(context)

    fun deduplicateSms(context: Context): Boolean = PrefsReader.deduplicateSms(context)

    fun getIpcToken(context: Context): String = PrefsReader.getIpcToken(context)

    private fun CoreXpRuntimeBridge.toRuntimeBridge(): RuntimeXpRuntimeBridge {
        val bridge = this
        return object : RuntimeXpRuntimeBridge {
            override fun capabilities(): RuntimeXpCapabilities {
                return bridge.capabilities().toRuntimeCapabilities()
            }

            override fun remotePrefsSource(group: String): RuntimePrefsSource {
                return bridge.remotePrefsSource(group).toRuntimePrefsSource()
            }
        }
    }

    private fun CoreXpCapabilities.toRuntimeCapabilities(): RuntimeXpCapabilities {
        return RuntimeXpCapabilities(
            frameworkName = frameworkName,
            frameworkVersion = frameworkVersion,
            frameworkApiVersion = frameworkApiVersion,
            frameworkPrivilege = frameworkPrivilege,
            frameworkProperties = frameworkProperties,
            supportsRemotePrefs = supportsRemotePrefs,
            supportsRemoteFile = supportsRemoteFile,
            supportsDeopt = supportsDeopt,
        )
    }

    private fun CorePrefsSource.toRuntimePrefsSource(): RuntimePrefsSource {
        val source = this
        return object : RuntimePrefsSource {
            override val sourceName: String = source.sourceName

            override fun readBoolean(
                context: Context,
                key: String,
                defaultValue: Boolean,
            ): RuntimePrefReadResult<Boolean>? {
                return source.readBoolean(context, key, defaultValue)?.toRuntimeResult()
            }

            override fun readString(
                context: Context,
                key: String,
                defaultValue: String,
            ): RuntimePrefReadResult<String>? {
                return source.readString(context, key, defaultValue)?.toRuntimeResult()
            }

            override fun readInt(
                context: Context,
                key: String,
                defaultValue: Int,
            ): RuntimePrefReadResult<Int>? {
                return source.readInt(context, key, defaultValue)?.toRuntimeResult()
            }
        }
    }

    private fun <T> CorePrefReadResult<T>.toRuntimeResult(): RuntimePrefReadResult<T> {
        return RuntimePrefReadResult(
            value = value,
            source = source,
        )
    }

    private fun XpMessageType.toRuntimeMessageType(): MessageType {
        return when (this) {
            XpMessageType.SMS_CODE -> MessageType.SMS_CODE
            XpMessageType.SMS_PLAIN -> MessageType.SMS_PLAIN
            XpMessageType.APP_NOTIFY -> MessageType.APP_NOTIFY
            XpMessageType.CALL_NOTIFY -> MessageType.CALL_NOTIFY
        }
    }
}
