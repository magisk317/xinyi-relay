package io.github.magisk317.relay.xp.runtime

import android.content.SharedPreferences
import io.github.magisk317.relay.xpbridge.bridge.NoopRemotePrefsSource
import io.github.magisk317.relay.xpbridge.bridge.PrefsSource
import io.github.magisk317.relay.xpbridge.bridge.XpCapabilities
import io.github.magisk317.relay.xpbridge.bridge.XpRuntimeBridge
import io.github.magisk317.smscode.runtime.common.prefs.SharedPrefsSource
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead

class LibXposedRuntimeBridge(
    private val runtimeHandle: Any?,
) : XpRuntimeBridge {
    private val snapshot: XpCapabilities by lazy { resolveCapabilities() }

    override fun capabilities(): XpCapabilities = snapshot

    override fun remotePrefsSource(group: String): PrefsSource {
        val handle = runtimeHandle ?: return NoopRemotePrefsSource
        val remotePrefs = callMethod(handle, "getRemotePreferences", group) ?: return NoopRemotePrefsSource
        if (remotePrefs is SharedPreferences) {
            return SharedPrefsSource(
                sourceName = REMOTE_PREFS_SOURCE_NAME,
                provider = { remotePrefs },
            )
        }
        return LibXposedRemotePrefsSource(remotePrefs)
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
        private val remotePrefs: Any,
    ) : PrefsSource {
        override val sourceName: String = REMOTE_PREFS_SOURCE_NAME

        override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> {
            return when (val value = readValue(key)) {
                is PrefRead.Hit -> {
                    val normalized = when (val raw = value.value) {
                        is Boolean -> raw
                        is Number -> raw.toInt() != 0
                        is String -> raw == "1" || raw.equals("true", ignoreCase = true)
                        else -> defaultValue
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
                        is String -> raw.toIntOrNull() ?: defaultValue
                        else -> defaultValue
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
