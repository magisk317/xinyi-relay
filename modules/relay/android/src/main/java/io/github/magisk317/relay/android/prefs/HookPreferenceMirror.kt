package io.github.magisk317.relay.android.prefs

import android.content.Context

object HookPreferenceMirror {
    suspend fun publish(context: Context): Boolean =
        // A missing provider is not a successful publication; the hook must not observe
        // an apparently synchronized state before the Xposed service is bound.
        AppPreferencesDataStore.syncToRemotePrefs(context) == true
}
