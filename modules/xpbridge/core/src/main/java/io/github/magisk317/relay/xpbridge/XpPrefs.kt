package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.prefs.NoopXpRuntimeBridge as NoopRuntimeXpRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.NoopXpPrefsRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpPrefsRuntimeBridge
import io.github.magisk317.relay.xpbridge.bridge.NoopXpRuntimeBridge as NoopCoreXpRuntimeBridge
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

    fun getCodeNotificationOwner(context: Context): String = prefsBridge.getCodeNotificationOwner(context)

    fun autoCancelCodeNotification(context: Context): Boolean = prefsBridge.autoCancelCodeNotification(context)

    fun getNotificationRetentionTime(context: Context): Int = prefsBridge.getNotificationRetentionTime(context)

    fun deduplicateSms(context: Context): Boolean = prefsBridge.deduplicateSms(context)

    fun getIpcToken(context: Context): String = prefsBridge.getIpcToken(context)

    private fun CoreXpRuntimeBridge.toCorePrefsAccess(): CorePrefsAccess {
        val bridge = this
        return object : CorePrefsAccess {
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !prefsBridge.isSensitiveDebugLogSupported()) {
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
