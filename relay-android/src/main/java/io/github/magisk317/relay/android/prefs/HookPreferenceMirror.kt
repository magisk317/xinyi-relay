package io.github.magisk317.relay.android.prefs

import android.content.Context

object HookPreferenceMirror {
    suspend fun publish(context: Context) {
        AppPreferencesDataStore.syncToRemotePrefs(context)
    }
}
