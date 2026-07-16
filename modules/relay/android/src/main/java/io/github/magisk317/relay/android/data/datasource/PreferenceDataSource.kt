package io.github.magisk317.relay.android.data.datasource

import io.github.magisk317.smscode.runtime.common.prefs.PreferenceChangeSet
import kotlinx.coroutines.flow.Flow

interface PreferenceDataSource {
    suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean
    suspend fun setBoolean(key: String, value: Boolean)

    suspend fun getString(key: String, defaultValue: String): String
    suspend fun setString(key: String, value: String)

    suspend fun getInt(key: String, defaultValue: Int): Int
    suspend fun setInt(key: String, value: Int)

    suspend fun getFloat(key: String, defaultValue: Float): Float
    suspend fun setFloat(key: String, value: Float)

    /**
     * Execute multiple writes atomically in a single DataStore transaction.
     * If any write fails, all changes are rolled back.
     */
    suspend fun batchEdit(block: suspend PreferenceWriteScope.() -> Unit)

    /** Persist a pre-staged shared change set atomically and report backend acceptance. */
    suspend fun persist(changes: PreferenceChangeSet): Boolean

    fun getBooleanFlow(key: String, defaultValue: Boolean): Flow<Boolean>
    fun getStringFlow(key: String, defaultValue: String): Flow<String>
    fun getIntFlow(key: String, defaultValue: Int): Flow<Int>
    fun getFloatFlow(key: String, defaultValue: Float): Flow<Float>
}

interface PreferenceWriteScope {
    suspend fun setBoolean(key: String, value: Boolean)
    suspend fun setString(key: String, value: String)
    suspend fun setInt(key: String, value: Int)
    suspend fun setFloat(key: String, value: Float)
}
