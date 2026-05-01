package io.github.magisk317.relay.prefs

import android.content.Context
import io.github.magisk317.relay.contract.prefs.PrefReadResult
import io.github.magisk317.relay.contract.prefs.PrefsSource
import io.github.magisk317.relay.contract.prefs.XpRuntimeBridge

internal object PrefsSourceChain {
    fun resolveSources(
        runtimeBridge: XpRuntimeBridge,
        warn: (String, Throwable?) -> Unit,
    ): List<PrefsSource> {
        val remoteSource = runCatching {
            runtimeBridge.remotePrefsSource(PrefsReader.PREFS_NAME)
        }.getOrElse {
            warn("PrefsReader: runtime bridge remote source resolve failed", it)
            null
        }
        return listOfNotNull(remoteSource.takeIf { runtimeBridge.capabilities().supportsRemotePrefs })
    }

    fun resolveBoolean(
        context: Context,
        key: String,
        defaultValue: Boolean,
        sources: List<PrefsSource>,
        logRuntimeBridgeOnce: () -> Unit,
        warn: (String, Throwable?) -> Unit,
    ): PrefReadResult<Boolean> {
        logRuntimeBridgeOnce()
        for (source in sources) {
            val result = runCatching { source.readBoolean(context, key, defaultValue) }
                .onFailure { warn("PrefsReader: source=${source.sourceName} bool key=$key failed", it) }
                .getOrNull()
            if (result != null) return result
        }
        return PrefReadResult(defaultValue, "default")
    }

    fun resolveString(
        context: Context,
        key: String,
        defaultValue: String,
        sources: List<PrefsSource>,
        logRuntimeBridgeOnce: () -> Unit,
        warn: (String, Throwable?) -> Unit,
    ): PrefReadResult<String> {
        logRuntimeBridgeOnce()
        for (source in sources) {
            val result = runCatching { source.readString(context, key, defaultValue) }
                .onFailure { warn("PrefsReader: source=${source.sourceName} string key=$key failed", it) }
                .getOrNull()
            if (result != null) return result
        }
        return PrefReadResult(defaultValue, "default")
    }

    fun resolveInt(
        context: Context,
        key: String,
        defaultValue: Int,
        sources: List<PrefsSource>,
        logRuntimeBridgeOnce: () -> Unit,
        warn: (String, Throwable?) -> Unit,
    ): PrefReadResult<Int> {
        logRuntimeBridgeOnce()
        for (source in sources) {
            val result = runCatching { source.readInt(context, key, defaultValue) }
                .onFailure { warn("PrefsReader: source=${source.sourceName} int key=$key failed", it) }
                .getOrNull()
            if (result != null) return result
        }
        return PrefReadResult(defaultValue, "default")
    }
}
