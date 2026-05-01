@file:Suppress("unused")

package io.github.magisk317.relay.data.secret

import android.content.Context
import kotlinx.coroutines.flow.Flow
import io.github.magisk317.relay.android.data.secret.InternalSecretStore as AndroidInternalSecretStore

object InternalSecretStore {
    fun getString(context: Context, key: String, defaultValue: String = ""): String =
        AndroidInternalSecretStore.getString(context, key, defaultValue)

    fun putString(context: Context, key: String, value: String) =
        AndroidInternalSecretStore.putString(context, key, value)

    fun observeString(context: Context, key: String, defaultValue: String = ""): Flow<String> =
        AndroidInternalSecretStore.observeString(context, key, defaultValue)

    suspend fun getOrMigrateString(
        context: Context,
        key: String,
        defaultValue: String = "",
        legacyValueProvider: suspend () -> String,
        legacyValueCleaner: suspend () -> Unit,
    ): String = AndroidInternalSecretStore.getOrMigrateString(context, key, defaultValue, legacyValueProvider, legacyValueCleaner)
}
