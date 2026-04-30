package io.github.magisk317.relay.prefs

import android.content.Context

object HookPreferenceMirror {
    suspend fun publish(context: Context) {
        AppPreferencesDataStore.syncToRemotePrefs(context)
    }
}
