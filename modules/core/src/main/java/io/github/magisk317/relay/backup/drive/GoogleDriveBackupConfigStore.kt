package io.github.magisk317.relay.backup.drive

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object GoogleDriveBackupConfigStore {
    private const val PREFS_NAME = "google_drive_backup_config_prefs"
    private const val KEY_CONFIG = "google_drive_backup_config"

    private val json = Json { ignoreUnknownKeys = true }

    fun getConfig(context: Context): GoogleDriveBackupConfig {
        val raw = getPrefs(context).getString(KEY_CONFIG, null) ?: return GoogleDriveBackupConfig()
        return runCatching {
            json.decodeFromString<GoogleDriveBackupConfig>(raw)
        }.getOrDefault(GoogleDriveBackupConfig())
    }

    fun saveConfig(context: Context, config: GoogleDriveBackupConfig) {
        getPrefs(context).edit().putString(KEY_CONFIG, json.encodeToString(config)).apply()
    }

    fun observeConfig(context: Context): Flow<GoogleDriveBackupConfig> {
        val appContext = context.applicationContext ?: context
        return callbackFlow {
            val prefs = getPrefs(appContext)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == KEY_CONFIG) {
                    trySend(getConfig(appContext))
                }
            }
            trySend(getConfig(appContext))
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }.distinctUntilChanged()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
