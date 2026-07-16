package io.github.magisk317.relay.android.prefs

import android.content.Context

object HookPreferenceMirror {
    suspend fun publish(context: Context): Boolean =
        AppPreferencesDataStore.syncToRemotePrefs(context) != false
}
