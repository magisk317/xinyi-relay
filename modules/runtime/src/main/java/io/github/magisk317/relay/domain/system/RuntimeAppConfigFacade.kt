package io.github.magisk317.relay.domain.system

import android.content.Context
import io.github.magisk317.relay.android.data.db.DBProvider
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime-side app config reads for hook / receiver entrypoints.
 */
class RuntimeAppConfigFacade(
    private val context: Context,
    private val appInfoLookup: suspend (String) -> AppInfo? = { packageName ->
        queryAppInfo(context, packageName)
    },
) {
    suspend fun isPackageBlocked(packageName: String): Boolean = withContext(Dispatchers.IO) {
        appInfoLookup(packageName)?.blocked ?: false
    }

    private companion object {
        private const val COLUMN_PACKAGE_NAME = "package_name"
        private const val COLUMN_BLOCKED = "blocked"

        fun queryAppInfo(context: Context, packageName: String): AppInfo? {
            if (packageName.isBlank()) return null
            val uri = DBProvider.appInfoContentUri(context)
                .buildUpon()
                .appendPath(packageName)
                .build()
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(COLUMN_PACKAGE_NAME, COLUMN_BLOCKED),
                null,
                null,
                null,
            ) ?: error("App-config provider unavailable")
            return cursor.use {
                if (!it.moveToFirst()) return@use null
                AppInfo(
                    packageName = it.getString(it.getColumnIndexOrThrow(COLUMN_PACKAGE_NAME)),
                    blocked = it.getInt(it.getColumnIndexOrThrow(COLUMN_BLOCKED)) != 0,
                )
            }
        }
    }
}
