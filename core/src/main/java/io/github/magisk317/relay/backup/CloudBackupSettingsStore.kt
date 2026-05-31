package io.github.magisk317.relay.backup

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CloudBackupSettingsStore {
    private const val PREFS_NAME = "cloud_backup_settings_prefs"
    private const val KEY_SETTINGS = "cloud_backup_settings"

    private val json = Json { ignoreUnknownKeys = true }

    fun getSettings(context: Context): CloudBackupSettings {
        val raw = getPrefs(context).getString(KEY_SETTINGS, null) ?: return CloudBackupSettings()
        return runCatching {
            json.decodeFromString<CloudBackupSettings>(raw)
        }.getOrDefault(CloudBackupSettings())
    }

    fun saveSettings(context: Context, settings: CloudBackupSettings) {
        getPrefs(context).edit().putString(KEY_SETTINGS, json.encodeToString(settings)).apply()
    }

    fun setAutoBackup(context: Context, source: BackupSource, enabled: Boolean) {
        saveSettings(
            context = context,
            settings = getSettings(context).copy(
                autoBackupEnabled = enabled,
                autoBackupSource = source,
            ),
        )
    }

    fun isAutoBackupEnabled(context: Context, source: BackupSource): Boolean {
        val settings = getSettings(context)
        return settings.autoBackupEnabled && settings.autoBackupSource == source
    }

    fun observeSettings(context: Context): Flow<CloudBackupSettings> {
        val appContext = context.applicationContext ?: context
        return callbackFlow {
            val prefs = getPrefs(appContext)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == KEY_SETTINGS) {
                    trySend(getSettings(appContext))
                }
            }
            trySend(getSettings(appContext))
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
