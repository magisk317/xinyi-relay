package io.github.magisk317.relay.xp.runtime

import android.content.SharedPreferences
import io.github.magisk317.relay.xpbridge.bridge.NoopRemotePrefsSource
import io.github.magisk317.relay.xpbridge.bridge.PrefsSource
import io.github.magisk317.relay.xpbridge.bridge.XpCapabilities
import io.github.magisk317.relay.xpbridge.bridge.XpRuntimeBridge
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.github.magisk317.xposed.preferences.PreferenceRead
import io.github.magisk317.xposed.preferences.PreferenceSource
import io.github.magisk317.xposed.preferences.SharedPreferencesSource

class LibXposedRuntimeBridge(
    private val runtimeHandle: Any?,
) : XpRuntimeBridge {
    private val snapshot: XpCapabilities by lazy { resolveCapabilities() }

    override fun capabilities(): XpCapabilities = snapshot

    override fun remotePrefsSource(group: String): PrefsSource {
        val handle = runtimeHandle ?: return NoopRemotePrefsSource
        return LibXposedRemotePrefsSource {
            callMethod(handle, "getRemotePreferences", group)
        }
    }

    private fun resolveCapabilities(): XpCapabilities {
        val interfaceClass = loadLibXposedInterfaceClass()
        val apiVersion = callMethod(runtimeHandle, "getApiVersion") as? Int
            ?: readStaticInt(interfaceClass, "LIB_API")
        val frameworkName = callMethod(runtimeHandle, "getFrameworkName") as? String ?: "libxposed"
        val frameworkVersion = callMethod(runtimeHandle, "getFrameworkVersion") as? String ?: "unknown"
        val properties = (callMethod(runtimeHandle, "getFrameworkProperties") as? Number)?.toLong()
        val supportsRemoteByProp = properties?.let {
            it and XpCapabilities.PROP_CAP_REMOTE != 0L
        } ?: false
        val supportsRemoteByMethod = hasMethod(runtimeHandle, "getRemotePreferences", 1) ||
            hasMethod(interfaceClass, "getRemotePreferences", 1)
        val supportsRemoteFileByMethod = (
            hasMethod(runtimeHandle, "listRemoteFiles", 0) &&
                hasMethod(runtimeHandle, "openRemoteFile", 1)
            ) || (
            hasMethod(interfaceClass, "listRemoteFiles", 0) &&
                hasMethod(interfaceClass, "openRemoteFile", 1)
            )
        val supportsDeopt = hasMethod(runtimeHandle, "deoptimize", 1) ||
            hasMethod(interfaceClass, "deoptimize", 1)

        return XpCapabilities(
            frameworkName = frameworkName,
            frameworkVersion = frameworkVersion,
            frameworkApiVersion = apiVersion,
            frameworkPrivilege = null,
            frameworkProperties = properties,
            supportsRemotePrefs = supportsRemoteByProp || supportsRemoteByMethod,
            supportsRemoteFile = supportsRemoteByProp || supportsRemoteFileByMethod,
            supportsDeopt = supportsDeopt,
        )
    }

    private fun loadLibXposedInterfaceClass(): Class<*>? = runCatching {
        Class.forName(LIB_XPOSED_INTERFACE_CLASS)
    }.getOrNull()

    private fun readStaticInt(clazz: Class<*>?, fieldName: String): Int? {
        if (clazz == null) return null
        return runCatching {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.getInt(null)
        }.getOrNull()
    }

    private fun callMethod(target: Any?, name: String, vararg args: Any?): Any? {
        if (target == null) return null
        return runCatching {
            val method = target.javaClass.methods.firstOrNull { m ->
                m.name == name && m.parameterTypes.size == args.size
            } ?: return null
            method.isAccessible = true
            method.invoke(target, *args)
        }.getOrNull()
    }

    private fun hasMethod(target: Any?, name: String, parameterSize: Int): Boolean {
        if (target == null) return false
        return hasMethod(target.javaClass, name, parameterSize)
    }

    private fun hasMethod(clazz: Class<*>?, name: String, parameterSize: Int): Boolean {
        if (clazz == null) return false
        return clazz.methods.any { it.name == name && it.parameterTypes.size == parameterSize }
    }

    private class LibXposedRemotePrefsSource(
        private val remotePrefsProvider: () -> Any?,
    ) : PrefsSource {
        override val sourceName: String = REMOTE_PREFS_SOURCE_NAME

        override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> {
            return currentSource()?.readBoolean(key, defaultValue) ?: PrefRead.Unavailable
        }

        override fun readString(key: String, defaultValue: String): PrefRead<String> {
            return currentSource()?.readString(key, defaultValue) ?: PrefRead.Unavailable
        }

        override fun readInt(key: String, defaultValue: Int): PrefRead<Int> {
            return currentSource()?.readInt(key, defaultValue) ?: PrefRead.Unavailable
        }

        private fun currentSource(): PrefsSource? {
            val remotePrefs = runCatching { remotePrefsProvider.invoke() }.getOrNull() ?: return null
            return if (remotePrefs is SharedPreferences) {
                KitPreferenceSourceAdapter(
                    SharedPreferencesSource(
                        sourceName = sourceName,
                        provider = { remotePrefs },
                    ),
                )
            } else {
                LibXposedReflectivePrefsSource(remotePrefs, sourceName)
            }
        }
    }

    /** Compatibility path for framework implementations that return a proxy rather than SharedPreferences. */
    private class LibXposedReflectivePrefsSource(
        private val remotePrefs: Any,
        override val sourceName: String,
    ) : PrefsSource {
        override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> {
            return when (val value = readValue(key)) {
                is PrefRead.Hit -> {
                    val normalized = when (val raw = value.value) {
                        is Boolean -> raw
                        is Number -> raw.toInt() != 0
                        is String -> when (raw.trim().lowercase()) {
                            "1", "true", "yes", "y", "on" -> true
                            "0", "false", "no", "n", "off" -> false
                            else -> return PrefRead.Unavailable
                        }
                        else -> return PrefRead.Unavailable
                    }
                    PrefRead.Hit(normalized, sourceName)
                }
                is PrefRead.Miss -> PrefRead.Miss
                is PrefRead.Unavailable -> PrefRead.Unavailable
            }
        }

        override fun readString(key: String, defaultValue: String): PrefRead<String> {
            return when (val value = readValue(key)) {
                is PrefRead.Hit -> {
                    val normalized = when (val raw = value.value) {
                        is String -> raw
                        null -> defaultValue
                        else -> raw.toString()
                    }
                    PrefRead.Hit(normalized, sourceName)
                }
                is PrefRead.Miss -> PrefRead.Miss
                is PrefRead.Unavailable -> PrefRead.Unavailable
            }
        }

        override fun readInt(key: String, defaultValue: Int): PrefRead<Int> {
            return when (val value = readValue(key)) {
                is PrefRead.Hit -> {
                    val normalized = when (val raw = value.value) {
                        is Int -> raw
                        is Long -> raw.toInt()
                        is Number -> raw.toInt()
                        is String -> raw.toIntOrNull() ?: return PrefRead.Unavailable
                        else -> return PrefRead.Unavailable
                    }
                    PrefRead.Hit(normalized, sourceName)
                }
                is PrefRead.Miss -> PrefRead.Miss
                is PrefRead.Unavailable -> PrefRead.Unavailable
            }
        }

        private fun readValue(key: String): PrefRead<Any?> {
            val all = callNoArg("getAll") as? Map<*, *> ?: return PrefRead.Unavailable
            if (!all.containsKey(key)) return PrefRead.Miss
            return PrefRead.Hit(all[key], sourceName)
        }

        private fun callNoArg(name: String): Any? = runCatching {
            val method = remotePrefs.javaClass.methods.firstOrNull { m ->
                m.name == name && m.parameterTypes.isEmpty()
            } ?: return null
            method.isAccessible = true
            method.invoke(remotePrefs)
        }.getOrNull()
    }

    private class KitPreferenceSourceAdapter(
        private val delegate: PreferenceSource,
    ) : PrefsSource {
        override val sourceName: String
            get() = delegate.sourceName

        override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> =
            delegate.readBoolean(key, defaultValue).toCoreResult()

        override fun readString(key: String, defaultValue: String): PrefRead<String> =
            delegate.readString(key, defaultValue).toCoreResult()

        override fun readInt(key: String, defaultValue: Int): PrefRead<Int> =
            delegate.readInt(key, defaultValue).toCoreResult()

        private fun <T> PreferenceRead<T>.toCoreResult(): PrefRead<T> = when (this) {
            is PreferenceRead.Hit -> PrefRead.Hit(value, source)
            PreferenceRead.Missing -> PrefRead.Miss
            PreferenceRead.Unavailable -> PrefRead.Unavailable
        }
    }

    companion object {
        private const val LIB_XPOSED_INTERFACE_CLASS = "io.github.libxposed.api.XposedInterface"
        private const val REMOTE_PREFS_SOURCE_NAME = "remote_libxposed"

        fun looksLikeLibXposedInterface(candidate: Any): Boolean {
            val methods = candidate.javaClass.methods.map { it.name }.toSet()
            return methods.contains("getFrameworkName") &&
                methods.contains("getFrameworkVersion") &&
                methods.contains("getRemotePreferences")
        }
    }
}
