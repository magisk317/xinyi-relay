package io.github.magisk317.relay.data.secret

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object InternalSecretStore {
    private const val PREFS_NAME = "internal_secret_prefs"

    fun getString(context: Context, key: String, defaultValue: String = ""): String {
        return getPrefs(context).getString(key, defaultValue) ?: defaultValue
    }

    fun putString(context: Context, key: String, value: String) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun observeString(context: Context, key: String, defaultValue: String = ""): Flow<String> {
        val appContext = context.applicationContext ?: context
        return callbackFlow {
            val prefs = getPrefs(appContext)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    trySend(readString(prefs, key, defaultValue))
                }
            }
            trySend(readString(prefs, key, defaultValue))
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }.distinctUntilChanged()
    }

    suspend fun getOrMigrateString(
        context: Context,
        key: String,
        defaultValue: String = "",
        legacyValueProvider: suspend () -> String,
        legacyValueCleaner: suspend () -> Unit,
    ): String {
        val current = getString(context, key, defaultValue)
        if (current.isNotBlank()) return current

        val legacy = legacyValueProvider()
        if (legacy.isBlank()) return current

        putString(context, key, legacy)
        legacyValueCleaner()
        return legacy
    }

    private fun getPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun readString(
        prefs: SharedPreferences,
        key: String,
        defaultValue: String,
    ): String = prefs.getString(key, defaultValue) ?: defaultValue
}
