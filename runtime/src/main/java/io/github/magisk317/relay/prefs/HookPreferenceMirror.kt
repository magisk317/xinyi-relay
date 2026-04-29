package io.github.magisk317.relay.prefs

import android.content.Context
import io.github.magisk317.relay.runtime.BuildConfig

object HookPreferenceMirror {
    fun requiresLegacyCompatMirror(): Boolean = BuildConfig.XPOSED_API_FLAVOR == "legacy"

    suspend fun publish(context: Context) {
        if (requiresLegacyCompatMirror()) {
            AppPreferencesDataStore.syncToSharedPrefs(context)
            AppPreferencesDataStore.ensureLegacySharedPrefsReadable(context)
        }
        AppPreferencesDataStore.syncToRemotePrefs(context)
    }
}
