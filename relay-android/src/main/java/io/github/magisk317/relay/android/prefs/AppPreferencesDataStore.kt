package io.github.magisk317.relay.android.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.constant.PrefRestoreTypeRegistry
import io.github.magisk317.relay.contract.constant.PrefValueType
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.smscode.domain.constant.SmsCodeConst
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Locale

object AppPreferencesDataStore {
    private val backupCompatTipShownKey = booleanPreferencesKey(PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN)
    private const val DATASTORE_FILE_NAME = "app_preferences.preferences_pb"
    private const val SHARED_PREFS_FILE_NAME = "xposed_prefs"

    @Volatile
    private var INSTANCE: DataStore<Preferences>? = null
    @Volatile
    private var resolvedDataStoreFile: File? = null

    private fun getInstance(context: Context): DataStore<Preferences> {
        INSTANCE?.let { return it }
        return synchronized(this) {
            INSTANCE ?: PreferenceDataStoreFactory.create {
                resolveDataStoreFile(context)
            }.also { INSTANCE = it }
        }
    }

    private fun getDataStoreFile(context: Context): File = resolveDataStoreFile(context)

    private fun getSharedPrefsFile(context: Context): File =
        File(context.dataDir, "shared_prefs/$SHARED_PREFS_FILE_NAME.xml")

    private fun getSharedPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE)

    private fun coerceBooleanValue(key: String, value: Boolean): Boolean {
        if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !PrefsReader.isSensitiveDebugLogSupported()) {
            return false
        }
        return value
    }

    fun normalizeTypedPrefValue(key: String, rawValue: Any?): Any? {
        return when (PrefRestoreTypeRegistry.typeOf(key)) {
            PrefValueType.BOOLEAN -> normalizeBooleanRawValue(rawValue)
            PrefValueType.INT -> normalizeIntRawValue(rawValue)
            PrefValueType.FLOAT -> normalizeFloatRawValue(rawValue)
            PrefValueType.STRING -> null
        }
    }

    private fun normalizeBooleanRawValue(rawValue: Any?): Boolean? {
        return when (rawValue) {
            is Boolean -> rawValue
            is Number -> rawValue.toInt() != 0
            is String -> parseBooleanRawValue(rawValue)
            else -> null
        }
    }

    private fun normalizeIntRawValue(rawValue: Any?): Int? {
        return when (rawValue) {
            is Int -> rawValue
            is Long -> rawValue.toInt()
            is Number -> rawValue.toInt()
            is String -> rawValue.trim().toIntOrNull()
            else -> null
        }
    }

    private fun normalizeFloatRawValue(rawValue: Any?): Float? {
        return when (rawValue) {
            is Float -> rawValue
            is Double -> rawValue.toFloat()
            is Number -> rawValue.toFloat()
            is String -> rawValue.trim().toFloatOrNull()
            else -> null
        }
    }

    private fun parseBooleanRawValue(rawValue: String): Boolean? {
        return when (rawValue.trim().lowercase(Locale.ROOT)) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
    }

    private fun isCanonicalTypedValue(type: PrefValueType, rawValue: Any?): Boolean {
        return when (type) {
            PrefValueType.BOOLEAN -> rawValue is Boolean
            PrefValueType.INT -> rawValue is Int
            PrefValueType.FLOAT -> rawValue is Float
            PrefValueType.STRING -> rawValue is String
        }
    }

    private fun ensureSharedPrefsReadable(context: Context) {
        val file = getSharedPrefsFile(context)
        StorageUtils.setFileWorldReadable(file, 3)
    }

    suspend fun repairKnownTypedPrefs(context: Context): Int {
        val sharedPrefs = getSharedPrefs(context)
        val snapshot = sharedPrefs.all
        if (snapshot.isEmpty()) return 0

        val repairedValues = linkedMapOf<String, Any>()
        val removedKeys = linkedSetOf<String>()

        snapshot.forEach { (key, rawValue) ->
            val type = PrefRestoreTypeRegistry.typeOf(key)
            if (type == PrefValueType.STRING) return@forEach

            val normalized = normalizeTypedPrefValue(key, rawValue)
            when {
                normalized == null -> removedKeys += key
                !isCanonicalTypedValue(type, rawValue) -> repairedValues[key] = normalized
            }
        }

        if (repairedValues.isEmpty() && removedKeys.isEmpty()) {
            return 0
        }

        sharedPrefs.edit().apply {
            removedKeys.forEach(::remove)
            repairedValues.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, coerceBooleanValue(key, value))
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                }
            }
        }.apply()
        ensureSharedPrefsReadable(context)

        getInstance(context).edit { prefs ->
            (removedKeys + repairedValues.keys).forEach { key ->
                prefs.remove(booleanPreferencesKey(key))
                prefs.remove(intPreferencesKey(key))
                prefs.remove(floatPreferencesKey(key))
                prefs.remove(stringPreferencesKey(key))
            }
            repairedValues.forEach { (key, value) ->
                when (value) {
                    is Boolean -> prefs[booleanPreferencesKey(key)] = coerceBooleanValue(key, value)
                    is Int -> prefs[intPreferencesKey(key)] = value
                    is Float -> prefs[floatPreferencesKey(key)] = value
                }
            }
        }
        remoteSyncPending = true
        remoteSyncPendingLogged = false

        val changedKeys = (removedKeys + repairedValues.keys).joinToString(",")
        XLog.w(
            "Typed prefs repaired: repaired=%d removed=%d keys=%s",
            repairedValues.size,
            removedKeys.size,
            changedKeys,
        )
        return repairedValues.size + removedKeys.size
    }

    suspend fun importMissingSharedPrefsIntoDataStore(context: Context): Int {
        val snapshot = getSharedPrefs(context).all
        if (snapshot.isEmpty()) return 0

        val existingKeys = getInstance(context).data.first().asMap().keys.map { it.name }.toSet()
        val imported = linkedMapOf<String, Any>()

        snapshot.forEach { (key, rawValue) ->
            if (existingKeys.contains(key)) return@forEach
            normalizeLegacySnapshotValue(key, rawValue)?.let { imported[key] = it }
        }

        if (imported.isEmpty()) return 0

        getInstance(context).edit { prefs ->
            imported.forEach { (key, value) ->
                when (value) {
                    is Boolean -> prefs[booleanPreferencesKey(key)] = coerceBooleanValue(key, value)
                    is Int -> prefs[intPreferencesKey(key)] = value
                    is Float -> prefs[floatPreferencesKey(key)] = value
                    is String -> prefs[stringPreferencesKey(key)] = value
                }
            }
        }

        remoteSyncPending = true
        remoteSyncPendingLogged = false
        XLog.w(
            "Imported missing shared prefs into DataStore: count=%d keys=%s",
            imported.size,
            imported.keys.joinToString(","),
        )
        return imported.size
    }

    private fun resolveDataStoreFile(context: Context): File {
        resolvedDataStoreFile?.let { return it }
        return synchronized(this) {
            resolvedDataStoreFile ?: buildDataStoreFile(context).also { resolvedDataStoreFile = it }
        }
    }

    private fun buildDataStoreFile(context: Context): File {
        val safeContext = context.applicationContext ?: context
        val primary = File(safeContext.dataDir, "datastore/$DATASTORE_FILE_NAME")
        prepareDataStoreCandidate(primary, label = "primary")?.let { return it }

        val fallback = File(safeContext.filesDir, "datastore/$DATASTORE_FILE_NAME")
        prepareDataStoreCandidate(fallback, label = "fallback")?.let { return it }

        XLog.w(
            "DataStore path fallback unresolved, using primary path anyway: %s",
            primary.absolutePath,
        )
        return primary
    }

    private fun prepareDataStoreCandidate(targetFile: File, label: String): File? {
        val parent = targetFile.parentFile ?: return targetFile
        if (parent.exists() && !parent.isDirectory) {
            val backup = File(
                parent.parentFile ?: targetFile.parentFile ?: targetFile,
                "${parent.name}.corrupted.${System.currentTimeMillis()}",
            )
            val moved = runCatching { parent.renameTo(backup) }.getOrDefault(false)
            XLog.w(
                "DataStore parent path is not a directory: label=%s path=%s moved=%s backup=%s",
                label,
                parent.absolutePath,
                moved,
                backup.absolutePath,
            )
            if (!moved) return null
        }
        if (!parent.exists() && !parent.mkdirs()) {
            XLog.w(
                "DataStore parent mkdirs failed: label=%s path=%s",
                label,
                parent.absolutePath,
            )
            return null
        }
        return targetFile
    }

    @Volatile
    private var remotePrefsProvider: (() -> SharedPreferences?)? = null
    @Volatile
    private var remoteProviderLogged = false
    @Volatile
    private var remoteSyncPending = false
    @Volatile
    private var remoteSyncPendingLogged = false

    fun setRemotePrefsProvider(provider: (() -> SharedPreferences?)?) {
        remotePrefsProvider = provider
        remoteProviderLogged = false
        if (provider == null) {
            // Keep pending flag for next service bind.
            remoteSyncPendingLogged = false
        }
    }

    fun hasPendingRemoteSync(): Boolean = remoteSyncPending

    private fun getRemotePrefs(): SharedPreferences? {
        val provider = remotePrefsProvider ?: return null
        return runCatching { provider.invoke() }.getOrElse { t ->
            if (!remoteProviderLogged) {
                remoteProviderLogged = true
                XLog.w("RemotePrefs provider failed: %s", t.message ?: t.javaClass.simpleName)
            }
            null
        }
    }

    suspend fun isBackupCompatTipShown(context: Context): Boolean = getInstance(context).data
        .map { prefs: Preferences -> prefs[backupCompatTipShownKey] ?: false }
        .first()

    suspend fun setBackupCompatTipShown(context: Context, shown: Boolean) {
        getInstance(context).edit { prefs ->
            prefs[backupCompatTipShownKey] = shown
        }
    }

    suspend fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !PrefsReader.isSensitiveDebugLogSupported()) {
            return false
        }
        val prefKey = booleanPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> coerceBooleanValue(key, safeRead(prefs, prefKey, defaultValue)) }
            .first()
    }

    suspend fun setBoolean(context: Context, key: String, value: Boolean) {
        val safeValue = coerceBooleanValue(key, value)
        val prefKey = booleanPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = safeValue
        }
    }

    suspend fun getString(context: Context, key: String, defaultValue: String): String {
        val prefKey = stringPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
            .first()
    }

    suspend fun setString(context: Context, key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    suspend fun getInt(context: Context, key: String, defaultValue: Int): Int {
        val prefKey = intPreferencesKey(key)
        return runCatching {
            getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
            .first()
        }.getOrElse {
            XLog.w(
                "DataStore int read failed key=%s err=%s",
                key,
                it.message ?: it.javaClass.simpleName,
            )
            defaultValue
        }
    }

    suspend fun setInt(context: Context, key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    suspend fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        val prefKey = floatPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
            .first()
    }

    suspend fun setFloat(context: Context, key: String, value: Float) {
        val prefKey = floatPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun syncToRemotePrefs(context: Context) {
        val prefs = getRemotePrefs() ?: run {
            remoteSyncPending = true
            if (!remoteSyncPendingLogged) {
                remoteSyncPendingLogged = true
                XLog.w("RemotePrefs sync pending: provider not available")
            }
            return
        }
        try {
            val editor = prefs.edit()
            editor.putBoolean(PrefConst.KEY_ENABLE, getBoolean(context, PrefConst.KEY_ENABLE, true))
            editor.putBoolean(
                PrefConst.KEY_SETTINGS_ACCORDION_MODE,
                getBoolean(context, PrefConst.KEY_SETTINGS_ACCORDION_MODE, true),
            )
            editor.putBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, getBoolean(context, PrefConst.KEY_VERBOSE_LOG_MODE, false))
            editor.putBoolean(
                PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
                getBoolean(context, PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false),
            )
            editor.putInt(
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                getInt(
                    context,
                    PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                    PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
                ),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
                getBoolean(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true),
            )
            editor.putString(
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
                getString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT),
            )
            editor.putString(
                PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
                getString(
                    context,
                    PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
                    PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
                ),
            )
            editor.putBoolean(PrefConst.KEY_SHOW_TOAST, getBoolean(context, PrefConst.KEY_SHOW_TOAST, true))
            editor.putString(
                PrefConst.KEY_SMSCODE_KEYWORDS,
                getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, SmsCodeConst.VERIFICATION_KEYWORDS_REGEX),
            )
            editor.putBoolean(PrefConst.KEY_MARK_AS_READ, getBoolean(context, PrefConst.KEY_MARK_AS_READ, false))
            editor.putBoolean(PrefConst.KEY_DELETE_SMS, getBoolean(context, PrefConst.KEY_DELETE_SMS, false))
            editor.putBoolean(
                PrefConst.KEY_COPY_TO_CLIPBOARD,
                getBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, false),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
                getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS,
                getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY,
                getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY,
                getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, true),
            )
            editor.putBoolean(PrefConst.KEY_BLOCK_SMS, getBoolean(context, PrefConst.KEY_BLOCK_SMS, false))
            editor.putBoolean(
                PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                getBoolean(context, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, true),
            )
            editor.putString(
                PrefConst.KEY_CODE_NOTIFICATION_RETENTION_TIME,
                getString(
                    context,
                    PrefConst.KEY_CODE_NOTIFICATION_RETENTION_TIME,
                    PrefConst.KEY_CODE_NOTIFICATION_RETENTION_TIME_DEFAULT,
                ),
            )
            editor.putString(
                PrefConst.KEY_APP_NOTIFY_CODE_RETENTION_TIME,
                getString(
                    context,
                    PrefConst.KEY_APP_NOTIFY_CODE_RETENTION_TIME,
                    PrefConst.KEY_APP_NOTIFY_CODE_RETENTION_TIME_DEFAULT,
                ),
            )
            editor.putString(
                PrefConst.KEY_CALL_NOTIFY_CODE_RETENTION_TIME,
                getString(
                    context,
                    PrefConst.KEY_CALL_NOTIFY_CODE_RETENTION_TIME,
                    PrefConst.KEY_CALL_NOTIFY_CODE_RETENTION_TIME_DEFAULT,
                ),
            )
            editor.putBoolean(
                PrefConst.KEY_DEDUPLICATE_SMS,
                getBoolean(context, PrefConst.KEY_DEDUPLICATE_SMS, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_SMS_BLACKLIST,
                getBoolean(context, PrefConst.KEY_ENABLE_SMS_BLACKLIST, false),
            )
            editor.putString(
                PrefConst.KEY_SMS_BLACKLIST_NUMBERS,
                getString(context, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, ""),
            )
            editor.putString(
                PrefConst.KEY_SMS_BLACKLIST_PREFIXES,
                getString(context, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, ""),
            )
            editor.putString(
                PrefConst.KEY_SMS_BLACKLIST_REGEX,
                getString(context, PrefConst.KEY_SMS_BLACKLIST_REGEX, ""),
            )
            editor.putString(
                PrefConst.KEY_SMS_BLACKLIST_CONTENT,
                getString(context, PrefConst.KEY_SMS_BLACKLIST_CONTENT, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
                getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, false),
            )
            editor.putBoolean(
                PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
                getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, false),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CALL_RELAY,
                getBoolean(context, PrefConst.KEY_ENABLE_CALL_RELAY, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_SMS_RELAY,
                getBoolean(context, PrefConst.KEY_ENABLE_SMS_RELAY, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_APP_RELAY,
                getBoolean(context, PrefConst.KEY_ENABLE_APP_RELAY, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
                getBoolean(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, false),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_KEYWORDS_EXCLUDE,
                getString(context, PrefConst.KEY_SMSCODE_KEYWORDS_EXCLUDE, ""),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_REGEX,
                getString(context, PrefConst.KEY_SMSCODE_REGEX, SmsCodeConst.VERIFICATION_KEYWORDS_REGEX),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_REGEX_EXCLUDE,
                getString(context, PrefConst.KEY_SMSCODE_REGEX_EXCLUDE, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_RELAY_BY_WIFI,
                getBoolean(context, PrefConst.KEY_RELAY_BY_WIFI, false),
            )
            editor.putBoolean(
                PrefConst.KEY_RELAY_BY_DATA,
                getBoolean(context, PrefConst.KEY_RELAY_BY_DATA, false),
            )
            editor.putString(
                PrefConst.KEY_RELAY_TEST,
                getString(context, PrefConst.KEY_RELAY_TEST, ""),
            )
            editor.putString(
                PrefConst.KEY_RELAY_KEYWORDS,
                getString(context, PrefConst.KEY_RELAY_KEYWORDS, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_RELAY_KEYWORDS_CASE_INSENSITIVE,
                getBoolean(context, PrefConst.KEY_RELAY_KEYWORDS_CASE_INSENSITIVE, true),
            )
            editor.putString(
                PrefConst.KEY_RELAY_KEYWORDS_REGEX,
                getString(context, PrefConst.KEY_RELAY_KEYWORDS_REGEX, ""),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_KEYWORDS_BLACKLIST,
                getString(context, PrefConst.KEY_SMSCODE_KEYWORDS_BLACKLIST, ""),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_KEYWORDS_WHITELIST,
                getString(context, PrefConst.KEY_SMSCODE_KEYWORDS_WHITELIST, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE,
                getBoolean(context, PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE, false),
            )
            editor.putString(
                PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN,
                getString(context, PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN, "5"),
            )
            editor.putBoolean(
                PrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK,
                getBoolean(context, PrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK, false),
            )
            editor.putBoolean(
                PrefConst.KEY_INTERNAL_ROOT_DB_BASELINE_INITED,
                getBoolean(context, PrefConst.KEY_INTERNAL_ROOT_DB_BASELINE_INITED, false),
            )
            editor.putString(
                PrefConst.KEY_INTERNAL_ROOT_DB_LAST_SMS_ID,
                getString(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_SMS_ID, "0"),
            )
            editor.putString(
                PrefConst.KEY_INTERNAL_ROOT_DB_LAST_CALL_ID,
                getString(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_CALL_ID, "0"),
            )
            editor.putString(
                PrefConst.KEY_IPC_TOKEN,
                getString(context, PrefConst.KEY_IPC_TOKEN, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_FORCE_STOP_RECOVERY,
                getBoolean(context, PrefConst.KEY_FORCE_STOP_RECOVERY, false),
            )
            editor.putBoolean(
                PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
                getBoolean(context, PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE, false),
            )
            val committed = editor.commit()
            if (!committed) {
                remoteSyncPending = true
                XLog.w("RemotePrefs sync failed: commit returned false")
            } else {
                remoteSyncPending = false
                remoteSyncPendingLogged = false
                verifyTokenSyncResult(prefs, context)
            }
        } catch (e: Exception) {
            remoteSyncPending = true
            XLog.w("RemotePrefs sync failed: %s", e.message ?: e.javaClass.simpleName)
        }
    }

    fun getBooleanFlow(context: Context, key: String, defaultValue: Boolean): Flow<Boolean> {
        if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !PrefsReader.isSensitiveDebugLogSupported()) {
            return kotlinx.coroutines.flow.flowOf(false)
        }
        val prefKey = booleanPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> coerceBooleanValue(key, safeRead(prefs, prefKey, defaultValue)) }
    }

    fun getStringFlow(context: Context, key: String, defaultValue: String): Flow<String> {
        val prefKey = stringPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
    }

    fun getIntFlow(context: Context, key: String, defaultValue: Int): Flow<Int> {
        val prefKey = intPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
    }

    fun getFloatFlow(context: Context, key: String, defaultValue: Float): Flow<Float> {
        val prefKey = floatPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
    }

    private fun normalizeLegacySnapshotValue(key: String, rawValue: Any?): Any? {
        return when (PrefRestoreTypeRegistry.typeOf(key)) {
            PrefValueType.BOOLEAN, PrefValueType.INT, PrefValueType.FLOAT ->
                normalizeTypedPrefValue(key, rawValue)

            PrefValueType.STRING -> when (rawValue) {
                null -> null
                is String -> rawValue
                else -> rawValue.toString()
            }
        }
    }

    private fun <T> safeRead(
        prefs: Preferences,
        key: Preferences.Key<T>,
        defaultValue: T,
    ): T {
        return runCatching {
            prefs[key] ?: defaultValue
        }.getOrElse {
            XLog.w(
                "DataStore type mismatch key=%s err=%s",
                key.name,
                it.message ?: it.javaClass.simpleName,
            )
            defaultValue
        }
    }

    private fun verifyTokenSyncResult(prefs: SharedPreferences, context: Context) {
        val token = prefs.getString(PrefConst.KEY_IPC_TOKEN, null)
        if (token.isNullOrBlank()) {
            XLog.w("RemotePrefs token verification: ipc_token is blank after sync")
            return
        }
        val verifyToken = PrefsReader.verifyTokenReadable(context)
        XLog.w(
            "RemotePrefs token verification: token=%s verify=%s",
            token.take(8).padEnd(8, '*'),
            verifyToken,
        )
    }

}
