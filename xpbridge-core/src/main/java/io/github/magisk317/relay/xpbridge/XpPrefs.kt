package io.github.magisk317.relay.xpbridge

import android.content.Context
import android.net.Uri
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.prefs.PrefsReader
import io.github.magisk317.relay.xpbridge.bridge.NoopXpRuntimeBridge
import io.github.magisk317.relay.xpbridge.bridge.PrefReadResult as CorePrefReadResult
import io.github.magisk317.relay.xpbridge.bridge.PrefsSource as CorePrefsSource
import io.github.magisk317.relay.xpbridge.bridge.XpCapabilities as CoreXpCapabilities
import io.github.magisk317.relay.xpbridge.bridge.XpRuntimeBridge as CoreXpRuntimeBridge
import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.prefs.CorePrefsAccess
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.relay.prefs.bridge.PrefReadResult as RuntimePrefReadResult
import io.github.magisk317.relay.prefs.bridge.PrefsSource as RuntimePrefsSource
import io.github.magisk317.relay.prefs.bridge.XpCapabilities as RuntimeXpCapabilities
import io.github.magisk317.relay.prefs.bridge.XpRuntimeBridge as RuntimeXpRuntimeBridge

object XpPrefs {
    private const val PREFS_NAME = "xposed_prefs"

    fun installRuntimeBridge(bridge: CoreXpRuntimeBridge?) {
        val activeBridge = bridge ?: NoopXpRuntimeBridge
        PrefsReader.installRuntimeBridge(activeBridge.toRuntimeBridge())
        CorePrefs.install(activeBridge.toCorePrefsAccess())
    }

    fun isEnabled(context: Context): Boolean = PrefsReader.isEnabled(context)

    fun isVerboseLogMode(context: Context): Boolean = PrefsReader.isVerboseLogMode(context)

    fun isSensitiveDebugLogMode(context: Context): Boolean = PrefsReader.isSensitiveDebugLogMode(context)

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

    fun getCodeNotificationOwner(context: Context): String = PrefsReader.getCodeNotificationOwner(context)

    fun autoCancelCodeNotification(context: Context): Boolean = PrefsReader.autoCancelCodeNotification(context)

    fun getNotificationRetentionTime(context: Context): Int = PrefsReader.getNotificationRetentionTime(context)

    fun deduplicateSms(context: Context): Boolean = PrefsReader.deduplicateSms(context)

    fun getIpcToken(context: Context): String = PrefsReader.getIpcToken(context)

    private fun CoreXpRuntimeBridge.toCorePrefsAccess(): CorePrefsAccess {
        val bridge = this
        return object : CorePrefsAccess {
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !PrefsReader.isSensitiveDebugLogSupported()) {
                    return false
                }
                val context = resolveCompatContext() ?: return defaultValue
                bridge.remotePrefsSource(PREFS_NAME).readBoolean(context, key, defaultValue)?.let { return it.value }
                if (bridge.capabilities().supportsRemotePrefs) return defaultValue
                return readBooleanFromCompatFallbacks(context, key, defaultValue)
            }

            override fun getString(key: String, defaultValue: String): String {
                val context = resolveCompatContext() ?: return defaultValue
                bridge.remotePrefsSource(PREFS_NAME).readString(context, key, defaultValue)?.let { return it.value }
                if (bridge.capabilities().supportsRemotePrefs) return defaultValue
                return readStringFromCompatFallbacks(context, key, defaultValue)
            }

            override fun getInt(key: String, defaultValue: Int): Int {
                val context = resolveCompatContext() ?: return defaultValue
                bridge.remotePrefsSource(PREFS_NAME).readInt(context, key, defaultValue)?.let { return it.value }
                if (bridge.capabilities().supportsRemotePrefs) return defaultValue
                return readIntFromCompatFallbacks(context, key, defaultValue)
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

    private fun readBooleanFromCompatFallbacks(context: Context, key: String, defaultValue: Boolean): Boolean {
        val defaultParam = if (defaultValue) "true" else "false"
        val providerUri = buildProviderUri(context, "bool", key, defaultParam)
        runCatching {
            context.contentResolver.query(providerUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val value = cursor.getString(0)
                    return value == "1" || value.equals("true", ignoreCase = true)
                }
            }
        }
        return runCatching {
            getSharedPrefs(context)?.getBoolean(key, defaultValue) ?: defaultValue
        }.getOrDefault(defaultValue)
    }

    private fun readStringFromCompatFallbacks(context: Context, key: String, defaultValue: String): String {
        val providerUri = buildProviderUri(context, "string", key, defaultValue)
        runCatching {
            context.contentResolver.query(providerUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0) ?: defaultValue
                }
            }
        }
        return runCatching {
            getSharedPrefs(context)?.getString(key, defaultValue) ?: defaultValue
        }.getOrDefault(defaultValue)
    }

    private fun readIntFromCompatFallbacks(context: Context, key: String, defaultValue: Int): Int {
        val providerUri = buildProviderUri(context, "int", key, defaultValue.toString())
        runCatching {
            context.contentResolver.query(providerUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)?.toIntOrNull() ?: defaultValue
                }
            }
        }
        return runCatching {
            val prefs = getSharedPrefs(context)
            when (val value = prefs?.all?.get(key)) {
                is Int -> value
                is Long -> value.toInt()
                is String -> value.toIntOrNull() ?: defaultValue
                else -> defaultValue
            }
        }.getOrDefault(defaultValue)
    }

    private fun buildProviderUri(
        context: Context,
        typePath: String,
        key: String,
        defaultValue: String,
    ): Uri {
        val authority = "${resolveModulePackageName(context)}.pref.provider"
        return Uri.parse("content://$authority/$typePath")
            .buildUpon()
            .appendQueryParameter("key", key)
            .appendQueryParameter("default", defaultValue)
            .build()
    }

    private fun resolveModulePackageName(context: Context): String {
        return CoreRuntime.access.applicationId.takeIf { it.isNotBlank() } ?: context.packageName
    }

    private fun getSharedPrefs(context: Context): android.content.SharedPreferences? {
        return runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrElse {
            runCatching {
                context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }.getOrNull()
        }
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
