package io.github.magisk317.relay.android.data.datasource

import android.content.Context
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceChangeSet
import kotlinx.coroutines.flow.Flow

class PreferenceDataSourceImpl(private val context: Context) : PreferenceDataSource {

    override suspend fun persist(changes: PreferenceChangeSet): Boolean =
        AppPreferencesDataStore.persistChanges(context, changes)

    override suspend fun batchEdit(block: suspend PreferenceWriteScope.() -> Unit) {
        AppPreferencesDataStore.batchEdit(context) {
            val scope = PreferenceWriteScopeImpl(this)
            scope.block()
        }
    }

    private class PreferenceWriteScopeImpl(
        private val delegate: AppPreferencesDataStore.BatchEditScope,
    ) : PreferenceWriteScope {
        override suspend fun setBoolean(key: String, value: Boolean) = delegate.setBoolean(key, value)
        override suspend fun setString(key: String, value: String) = delegate.setString(key, value)
        override suspend fun setInt(key: String, value: Int) = delegate.setInt(key, value)
        override suspend fun setFloat(key: String, value: Float) = delegate.setFloat(key, value)
    }

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return AppPreferencesDataStore.getBoolean(context, key, defaultValue)
    }

    override suspend fun setBoolean(key: String, value: Boolean) {
        AppPreferencesDataStore.setBoolean(context, key, value)
    }

    override suspend fun getString(key: String, defaultValue: String): String {
        return AppPreferencesDataStore.getString(context, key, defaultValue)
    }

    override suspend fun setString(key: String, value: String) {
        AppPreferencesDataStore.setString(context, key, value)
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int {
        return AppPreferencesDataStore.getInt(context, key, defaultValue)
    }

    override suspend fun setInt(key: String, value: Int) {
        AppPreferencesDataStore.setInt(context, key, value)
    }

    override suspend fun getFloat(key: String, defaultValue: Float): Float {
        return AppPreferencesDataStore.getFloat(context, key, defaultValue)
    }

    override suspend fun setFloat(key: String, value: Float) {
        AppPreferencesDataStore.setFloat(context, key, value)
    }

    override fun getBooleanFlow(key: String, defaultValue: Boolean): Flow<Boolean> {
        return AppPreferencesDataStore.getBooleanFlow(context, key, defaultValue)
    }

    override fun getStringFlow(key: String, defaultValue: String): Flow<String> {
        return AppPreferencesDataStore.getStringFlow(context, key, defaultValue)
    }

    override fun getIntFlow(key: String, defaultValue: Int): Flow<Int> {
        return AppPreferencesDataStore.getIntFlow(context, key, defaultValue)
    }

    override fun getFloatFlow(key: String, defaultValue: Float): Flow<Float> {
        return AppPreferencesDataStore.getFloatFlow(context, key, defaultValue)
    }
}
