package io.github.magisk317.relay.android.prefs

import io.github.magisk317.relay.contract.prefs.PrefsSource
import io.github.magisk317.relay.contract.prefs.XpRuntimeBridge

internal object PrefsSourceChain {
    fun resolveSources(
        runtimeBridge: XpRuntimeBridge,
        warn: (String, Throwable?) -> Unit,
    ): List<PrefsSource> {
        val supportsRemotePrefs = runCatching {
            runtimeBridge.capabilities().supportsRemotePrefs
        }.getOrElse {
            warn("PrefsReader: runtime bridge capabilities resolve failed", it)
            false
        }
        if (!supportsRemotePrefs) {
            return emptyList()
        }
        val remoteSource = runCatching {
            runtimeBridge.remotePrefsSource(PrefsReader.REMOTE_PREFS_GROUP)
        }.getOrElse {
            warn("PrefsReader: runtime bridge remote source resolve failed", it)
            null
        }
        return listOfNotNull(remoteSource)
    }
}
