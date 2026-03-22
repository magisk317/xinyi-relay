package io.github.magisk317.relay.xp.runtime

import android.content.Context
import io.github.magisk317.relay.xp.bridge.NoopRemotePrefsSource
import io.github.magisk317.relay.xp.bridge.PrefReadResult
import io.github.magisk317.relay.xp.bridge.PrefsSource
import io.github.magisk317.relay.xp.bridge.XpCapabilities
import io.github.magisk317.relay.xp.bridge.XpRuntimeBridge

class LibXposedRuntimeBridge(
    private val runtimeHandle: Any?,
) : XpRuntimeBridge {
    private val snapshot: XpCapabilities by lazy { resolveCapabilities() }

    override fun capabilities(): XpCapabilities = snapshot

    override fun remotePrefsSource(group: String): PrefsSource {
        val handle = runtimeHandle ?: return NoopRemotePrefsSource
        val remotePrefs = callMethod(handle, "getRemotePreferences", group) ?: return NoopRemotePrefsSource
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
        override val sourceName: String = "remote_libxposed"

        override fun readBoolean(context: Context, key: String, defaultValue: Boolean): PrefReadResult<Boolean>? {
            val value = readValue(key) ?: return null
            val normalized = when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value == "1" || value.equals("true", ignoreCase = true)
                else -> defaultValue
            }
            return PrefReadResult(normalized, sourceName)
        }

        override fun readString(context: Context, key: String, defaultValue: String): PrefReadResult<String>? {
            val value = readValue(key) ?: return null
            val normalized = when (value) {
                is String -> value
                else -> value.toString()
            }
            return PrefReadResult(normalized, sourceName)
        }

        override fun readInt(context: Context, key: String, defaultValue: Int): PrefReadResult<Int>? {
            val value = readValue(key) ?: return null
            val normalized = when (value) {
                is Int -> value
                is Long -> value.toInt()
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: defaultValue
                else -> defaultValue
            }
            return PrefReadResult(normalized, sourceName)
        }

        private fun readValue(key: String): Any? {
            val all = callNoArg("getAll") as? Map<*, *> ?: return null
            if (!all.containsKey(key)) return null
            return all[key]
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

        fun looksLikeLibXposedInterface(candidate: Any): Boolean {
            val methods = candidate.javaClass.methods.map { it.name }.toSet()
            return methods.contains("getFrameworkName") &&
                methods.contains("getFrameworkVersion") &&
                methods.contains("getRemotePreferences")
        }
    }
}
