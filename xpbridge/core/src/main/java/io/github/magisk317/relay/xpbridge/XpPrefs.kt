package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.relay.xpbridge.bridge.NoopXpRuntimeBridge
import io.github.magisk317.relay.xpbridge.bridge.PrefReadResult as CorePrefReadResult
import io.github.magisk317.relay.xpbridge.bridge.PrefsSource as CorePrefsSource
import io.github.magisk317.relay.xpbridge.bridge.XpCapabilities as CoreXpCapabilities
import io.github.magisk317.relay.xpbridge.bridge.XpRuntimeBridge as CoreXpRuntimeBridge
import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.prefs.CorePrefsAccess
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.relay.contract.prefs.PrefReadResult as RuntimePrefReadResult
import io.github.magisk317.relay.contract.prefs.PrefsSource as RuntimePrefsSource
import io.github.magisk317.relay.contract.prefs.XpCapabilities as RuntimeXpCapabilities
import io.github.magisk317.relay.contract.prefs.XpRuntimeBridge as RuntimeXpRuntimeBridge

object XpPrefs {
    private const val PREFS_NAME = "xposed_prefs"
    private val prefsAccess: XpPrefsAccess = PrefsReaderXpPrefsAccess

    fun installRuntimeBridge(bridge: CoreXpRuntimeBridge?) {
        val activeBridge = bridge ?: NoopXpRuntimeBridge
        prefsAccess.installRuntimeBridge(activeBridge.toRuntimeBridge())
        CorePrefs.install(activeBridge.toCorePrefsAccess())
    }

    fun isEnabled(context: Context): Boolean = prefsAccess.isEnabled(context)

    fun isVerboseLogMode(context: Context): Boolean = prefsAccess.isVerboseLogMode(context)

    fun isSensitiveDebugLogMode(context: Context): Boolean = prefsAccess.isSensitiveDebugLogMode(context)

    fun relayFeaturesEnabled(context: Context): Boolean = prefsAccess.relayFeaturesEnabled(context)

    fun autoInputCodeEnabled(context: Context): Boolean = prefsAccess.autoInputCodeEnabled(context)

    fun autoEnterCodeEnabled(context: Context): Boolean = prefsAccess.autoEnterCodeEnabled(context)

    fun getAutoInputCodeDelay(context: Context): Long = prefsAccess.getAutoInputCodeDelay(context)

    fun getAutoInputCodeIntervalMs(context: Context): Long = prefsAccess.getAutoInputCodeIntervalMs(context)

    fun shouldShowToast(context: Context): Boolean = prefsAccess.shouldShowToast(context)

    fun markAsReadEnabled(context: Context): Boolean = prefsAccess.markAsReadEnabled(context)

    fun deleteSmsEnabled(context: Context): Boolean = prefsAccess.deleteSmsEnabled(context)

    fun copyToClipboardEnabled(context: Context): Boolean = prefsAccess.copyToClipboardEnabled(context)

    fun isMessageTypeEnabled(context: Context, messageType: XpMessageType): Boolean {
        return prefsAccess.isMessageTypeEnabled(context, messageType)
    }

    fun recordSmsCodeEnabled(context: Context): Boolean = prefsAccess.recordSmsCodeEnabled(context)

    fun blockSmsEnabled(context: Context): Boolean = prefsAccess.blockSmsEnabled(context)

    fun showCodeNotification(context: Context): Boolean = prefsAccess.showCodeNotification(context)

    fun getCodeNotificationOwner(context: Context): String = prefsAccess.getCodeNotificationOwner(context)

    fun autoCancelCodeNotification(context: Context): Boolean = prefsAccess.autoCancelCodeNotification(context)

    fun getNotificationRetentionTime(context: Context): Int = prefsAccess.getNotificationRetentionTime(context)

    fun deduplicateSms(context: Context): Boolean = prefsAccess.deduplicateSms(context)

    fun getIpcToken(context: Context): String = prefsAccess.getIpcToken(context)

    private fun CoreXpRuntimeBridge.toCorePrefsAccess(): CorePrefsAccess {
        val bridge = this
        return object : CorePrefsAccess {
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !prefsAccess.isSensitiveDebugLogSupported()) {
                    return false
                }
                val context = resolveCompatContext() ?: return defaultValue
                bridge.remotePrefsSource(PREFS_NAME).readBoolean(context, key, defaultValue)?.let { return it.value }
                return defaultValue
            }

            override fun getString(key: String, defaultValue: String): String {
                val context = resolveCompatContext() ?: return defaultValue
                bridge.remotePrefsSource(PREFS_NAME).readString(context, key, defaultValue)?.let { return it.value }
                return defaultValue
            }

            override fun getInt(key: String, defaultValue: Int): Int {
                val context = resolveCompatContext() ?: return defaultValue
                bridge.remotePrefsSource(PREFS_NAME).readInt(context, key, defaultValue)?.let { return it.value }
                return defaultValue
            }
        }
    }

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

    private fun resolveCompatContext(): Context? {
        val application = runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass.getMethod("currentApplication")
            currentApplication.invoke(null) as? Context
        }.getOrNull()
        if (application != null) return application

        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentThread = activityThreadClass.getMethod("currentActivityThread").invoke(null) ?: return@runCatching null
            val systemContext = currentThread.javaClass.methods.firstOrNull {
                it.name == "getSystemContext" && it.parameterTypes.isEmpty()
            } ?: return@runCatching null
            systemContext.invoke(currentThread) as? Context
        }.getOrNull()
    }

}

private interface XpPrefsAccess {
    fun installRuntimeBridge(bridge: RuntimeXpRuntimeBridge?)
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

private object PrefsReaderXpPrefsAccess : XpPrefsAccess {
    override fun installRuntimeBridge(bridge: RuntimeXpRuntimeBridge?) {
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
}

private fun XpMessageType.toRuntimeMessageType(): MessageType {
    return when (this) {
        XpMessageType.SMS_CODE -> MessageType.SMS_CODE
        XpMessageType.SMS_PLAIN -> MessageType.SMS_PLAIN
        XpMessageType.APP_NOTIFY -> MessageType.APP_NOTIFY
        XpMessageType.CALL_NOTIFY -> MessageType.CALL_NOTIFY
    }
}
