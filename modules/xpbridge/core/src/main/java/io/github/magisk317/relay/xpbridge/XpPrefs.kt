package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.prefs.NoopXpRuntimeBridge as NoopRuntimeXpRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.NoopXpPrefsRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpPrefsRuntimeBridge
import io.github.magisk317.relay.xpbridge.bridge.NoopXpRuntimeBridge as NoopCoreXpRuntimeBridge
import io.github.magisk317.relay.xpbridge.bridge.XpCapabilities as CoreXpCapabilities
import io.github.magisk317.relay.xpbridge.bridge.XpRuntimeBridge as CoreXpRuntimeBridge
import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.prefs.CorePrefsAccess
import io.github.magisk317.relay.contract.prefs.XpCapabilities as RuntimeXpCapabilities
import io.github.magisk317.relay.contract.prefs.XpRuntimeBridge as RuntimeXpRuntimeBridge
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead

object XpPrefs {
    private const val PREFS_NAME = "xposed_prefs"
    @Volatile
    private var prefsBridge: XpPrefsRuntimeBridge = NoopXpPrefsRuntimeBridge

    @Volatile
    private var installedRuntimeBridge: RuntimeXpRuntimeBridge = NoopRuntimeXpRuntimeBridge

    fun installPlatformBridge(bridge: XpPrefsRuntimeBridge?) {
        val activeBridge = bridge ?: NoopXpPrefsRuntimeBridge
        prefsBridge = activeBridge
        activeBridge.installRuntimeBridge(installedRuntimeBridge)
    }

    fun installRuntimeBridge(bridge: CoreXpRuntimeBridge?) {
        val activeBridge = bridge ?: NoopCoreXpRuntimeBridge
        installedRuntimeBridge = activeBridge.toRuntimeBridge()
        prefsBridge.installRuntimeBridge(installedRuntimeBridge)
        CorePrefs.install(activeBridge.toCorePrefsAccess())
    }

    fun isEnabled(context: Context): Boolean = prefsBridge.isEnabled(context)
    fun isVerboseLogMode(context: Context): Boolean = prefsBridge.isVerboseLogMode(context)
    fun isSensitiveDebugLogMode(context: Context): Boolean = prefsBridge.isSensitiveDebugLogMode(context)
    fun relayFeaturesEnabled(context: Context): Boolean = prefsBridge.relayFeaturesEnabled(context)
    fun mobileAutomationAllowed(context: Context): Boolean = prefsBridge.mobileAutomationAllowed(context)
    fun autoInputCodeEnabled(context: Context): Boolean = prefsBridge.autoInputCodeEnabled(context)
    fun autoEnterCodeEnabled(context: Context): Boolean = prefsBridge.autoEnterCodeEnabled(context)
    fun getAutoInputCodeDelay(context: Context): Long = prefsBridge.getAutoInputCodeDelay(context)
    fun getAutoInputCodeIntervalMs(context: Context): Long = prefsBridge.getAutoInputCodeIntervalMs(context)
    fun shouldShowToast(context: Context): Boolean = prefsBridge.shouldShowToast(context)
    fun markAsReadEnabled(context: Context): Boolean = prefsBridge.markAsReadEnabled(context)
    fun deleteSmsEnabled(context: Context): Boolean = prefsBridge.deleteSmsEnabled(context)
    fun copyToClipboardEnabled(context: Context): Boolean = prefsBridge.copyToClipboardEnabled(context)
    fun isMessageTypeEnabled(context: Context, messageType: XpMessageType): Boolean {
        return prefsBridge.isMessageTypeEnabled(context, messageType)
    }
    fun recordSmsCodeEnabled(context: Context): Boolean = prefsBridge.recordSmsCodeEnabled(context)
    fun blockSmsEnabled(context: Context): Boolean = prefsBridge.blockSmsEnabled(context)
    fun showCodeNotification(context: Context): Boolean = prefsBridge.showCodeNotification(context)
    fun autoCancelCodeNotification(context: Context): Boolean = prefsBridge.autoCancelCodeNotification(context)
    fun getNotificationRetentionTime(context: Context): Int = prefsBridge.getNotificationRetentionTime(context)
    fun deduplicateSms(context: Context): Boolean = prefsBridge.deduplicateSms(context)
    fun getIpcToken(context: Context): String = prefsBridge.getIpcToken(context)

    private fun CoreXpRuntimeBridge.toRuntimeBridge(): RuntimeXpRuntimeBridge {
        val bridge = this
        return object : RuntimeXpRuntimeBridge {
            override fun capabilities(): RuntimeXpCapabilities {
                return bridge.capabilities().toRuntimeCapabilities()
            }

            override fun remotePrefsSource(group: String) = bridge.remotePrefsSource(group)
        }
    }

    private fun <T> PrefRead<T>.valueOr(defaultValue: T): T {
        return when (this) {
            is PrefRead.Hit -> value
            is PrefRead.Miss,
            is PrefRead.Unavailable,
            -> defaultValue
        }
    }

    private fun CoreXpRuntimeBridge.getBooleanPref(key: String, defaultValue: Boolean): Boolean {
        return remotePrefsSource(PREFS_NAME).readBoolean(key, defaultValue).valueOr(defaultValue)
    }

    private fun CoreXpRuntimeBridge.getStringPref(key: String, defaultValue: String): String {
        return remotePrefsSource(PREFS_NAME).readString(key, defaultValue).valueOr(defaultValue)
    }

    private fun CoreXpRuntimeBridge.getIntPref(key: String, defaultValue: Int): Int {
        return remotePrefsSource(PREFS_NAME).readInt(key, defaultValue).valueOr(defaultValue)
    }

    private fun CoreXpRuntimeBridge.toCorePrefsAccess(): CorePrefsAccess {
        val bridge = this
        return object : CorePrefsAccess {
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !prefsBridge.isSensitiveDebugLogSupported()) {
                    return false
                }
                return bridge.getBooleanPref(key, defaultValue)
            }

            override fun getString(key: String, defaultValue: String): String {
                return bridge.getStringPref(key, defaultValue)
            }

            override fun getInt(key: String, defaultValue: Int): Int {
                return bridge.getIntPref(key, defaultValue)
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
}
