package io.github.magisk317.relay.data.datasource

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

    fun getBooleanFlow(key: String, defaultValue: Boolean): Flow<Boolean>
    fun getStringFlow(key: String, defaultValue: String): Flow<String>
    fun getIntFlow(key: String, defaultValue: Int): Flow<Int>
    fun getFloatFlow(key: String, defaultValue: Float): Flow<Float>
}
