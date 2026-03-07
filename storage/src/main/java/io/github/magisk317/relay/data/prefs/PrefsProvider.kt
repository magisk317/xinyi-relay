package io.github.magisk317.relay.data.prefs

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import io.github.magisk317.relay.storage.BuildConfig
import io.github.magisk317.relay.common.utils.AppPreferencesDataStore
import io.github.magisk317.relay.common.utils.XLog
import kotlinx.coroutines.runBlocking

class PrefsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        if (!isCallerAllowed(ctx)) {
            XLog.w("PrefsProvider: deny caller uid=%d", Binder.getCallingUid())
            return null
        }
        val type = sUriMatcher.match(uri)
        val key = uri.getQueryParameter("key") ?: return null
        val defaultValue = uri.getQueryParameter("default")
        val cursor = MatrixCursor(arrayOf(COLUMN_VALUE))

        when (type) {
            TYPE_BOOL -> {
                val def = defaultValue?.toBooleanStrictOrNull() ?: false
                val value = runBlocking { AppPreferencesDataStore.getBoolean(ctx, key, def) }
                cursor.addRow(arrayOf(if (value) "1" else "0"))
            }

            TYPE_STRING -> {
                val def = defaultValue ?: ""
                val value = runBlocking { AppPreferencesDataStore.getString(ctx, key, def) }
                cursor.addRow(arrayOf(value))
            }

            TYPE_INT -> {
                val def = defaultValue?.toIntOrNull() ?: 0
                val value = runBlocking { AppPreferencesDataStore.getInt(ctx, key, def) }
                cursor.addRow(arrayOf(value.toString()))
            }

            else -> return null
        }
        return cursor
    }

    private fun isCallerAllowed(ctx: Context): Boolean {
        val uid = Binder.getCallingUid()
        // 1. Check for standard System UIDs
        if (uid == Process.SYSTEM_UID || uid == Process.PHONE_UID) return true
        // 2. Check for self
        if (uid == ctx.applicationInfo?.uid) return true

        // 3. Check if the caller is a System App
        try {
            val packages = ctx.packageManager.getPackagesForUid(uid) ?: return false
            for (packageName in packages) {
                // If any package sharing this UID is a system app, allow it.
                if (isSystemApp(ctx, packageName)) {
                    return true
                }
            }
        } catch (ignored: Exception) {
            // It's safe to ignore as we default to false
            return false
        }

        return false
    }

    private fun isSystemApp(context: Context, packageName: String): Boolean = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        (
            info.flags and
                (
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM or
                        android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                    )
            ) !=
            0
    } catch (ignored: Exception) {
        false
    }

    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".pref.provider"
        private const val PATH_BOOL = "bool"
        private const val PATH_STRING = "string"
        private const val PATH_INT = "int"
        private const val TYPE_BOOL = 1
        private const val TYPE_STRING = 2
        private const val TYPE_INT = 3
        private const val COLUMN_VALUE = "value"

        private val sUriMatcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_BOOL, TYPE_BOOL)
            addURI(AUTHORITY, PATH_STRING, TYPE_STRING)
            addURI(AUTHORITY, PATH_INT, TYPE_INT)
        }

        @JvmField
        val BOOL_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_BOOL")

        @JvmField
        val STRING_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_STRING")

        @JvmField
        val INT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_INT")
    }
}
