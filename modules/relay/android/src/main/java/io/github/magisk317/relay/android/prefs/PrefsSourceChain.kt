package io.github.magisk317.relay.android.prefs

import android.content.Context
import io.github.magisk317.relay.contract.prefs.PrefsSource
import io.github.magisk317.relay.contract.prefs.XpRuntimeBridge

internal object PrefsSourceChain {
    private val localSource = LocalPrefsSource(PrefsReader.PREFS_NAME)

    /**
     * Set the hook process context for local SharedPreferences fallback.
     * Should be called once when the plugin context becomes available in hook process.
     */
    fun setHookContext(context: Context) {
        localSource.setHookContext(context)
    }

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
        // Remote first, local SharedPreferences as fallback (mirrors XposedSmsCode's getAnyPrefs())
        return listOfNotNull(
            remoteSource.takeIf { runtimeBridge.capabilities().supportsRemotePrefs },
            localSource,
        )
    }
}
