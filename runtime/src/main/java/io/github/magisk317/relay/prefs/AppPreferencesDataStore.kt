package io.github.magisk317.relay.prefs

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
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.StorageUtils
import io.github.magisk317.relay.common.utils.XLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

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

    private fun ensureDataStoreReadable(context: Context) {
        val file = getDataStoreFile(context)
        StorageUtils.setFileWorldReadable(file, 3)
    }

    private fun ensureSharedPrefsReadable(context: Context) {
        val file = getSharedPrefsFile(context)
        StorageUtils.setFileWorldReadable(file, 3)
    }

    fun ensureReadable(context: Context) {
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
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
        getSharedPrefs(context).edit().putBoolean(PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN, shown).apply()
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
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
        getSharedPrefs(context).edit().putBoolean(key, safeValue).apply()
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
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
        getSharedPrefs(context).edit().putString(key, value).apply()
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
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
            recoverIntFromSharedPrefs(context, key, defaultValue)
        }
    }

    suspend fun setInt(context: Context, key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
        getSharedPrefs(context).edit().putInt(key, value).apply()
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
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
        getSharedPrefs(context).edit().putFloat(key, value).apply()
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
    }

    suspend fun getBooleanCompat(context: Context, key: String, defaultValue: Boolean): Boolean {
        if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !PrefsReader.isSensitiveDebugLogSupported()) {
            return false
        }
        val sharedPrefs = getSharedPrefs(context)
        return if (sharedPrefs.contains(key)) {
            runCatching {
                coerceBooleanValue(key, sharedPrefs.getBoolean(key, defaultValue))
            }.getOrElse {
                XLog.w(
                    "SharedPreferences boolean type mismatch key=%s err=%s",
                    key,
                    it.message ?: it.javaClass.simpleName,
                )
                getBoolean(context, key, defaultValue)
            }
        } else {
            getBoolean(context, key, defaultValue)
        }
    }

    suspend fun getStringCompat(context: Context, key: String, defaultValue: String): String {
        val sharedPrefs = getSharedPrefs(context)
        return if (sharedPrefs.contains(key)) {
            sharedPrefs.getString(key, defaultValue) ?: defaultValue
        } else {
            getString(context, key, defaultValue)
        }
    }

    suspend fun getIntCompat(context: Context, key: String, defaultValue: Int): Int {
        val sharedPrefs = getSharedPrefs(context)
        return if (sharedPrefs.contains(key)) {
            runCatching {
                sharedPrefs.getInt(key, defaultValue)
            }.getOrElse {
                recoverIntFromSharedPrefs(context, key, defaultValue)
            }
        } else {
            getInt(context, key, defaultValue)
        }
    }

    suspend fun syncToSharedPrefs(context: Context) {
        val editor = getSharedPrefs(context).edit()
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
            PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB,
            getInt(
                context,
                PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB,
                PrefConst.RUNTIME_LOG_FILE_SIZE_MB_DEFAULT,
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
            getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.SMSCODE_KEYWORDS_DEFAULT),
        )
        editor.putBoolean(PrefConst.KEY_MARK_AS_READ, getBoolean(context, PrefConst.KEY_MARK_AS_READ, false))
        editor.putBoolean(PrefConst.KEY_DELETE_SMS, getBoolean(context, PrefConst.KEY_DELETE_SMS, false))
        editor.putBoolean(PrefConst.KEY_COPY_TO_CLIPBOARD, getBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, false))
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS,
            getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
            getBoolean(
                context,
                PrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
                getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true),
            ),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS,
            getBoolean(
                context,
                PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS,
                getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true),
            ),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY,
            getBoolean(
                context,
                PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY,
                getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true),
            ),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY,
            getBoolean(
                context,
                PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY,
                getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true),
            ),
        )
        editor.putBoolean(PrefConst.KEY_BLOCK_SMS, getBoolean(context, PrefConst.KEY_BLOCK_SMS, false))
        editor.putBoolean(
            PrefConst.KEY_FORCE_STOP_RECOVERY,
            getBoolean(context, PrefConst.KEY_FORCE_STOP_RECOVERY, false),
        )
        editor.putBoolean(
            PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
            getBoolean(context, PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE, false),
        )
        editor.putBoolean(
            PrefConst.KEY_SHOW_CODE_NOTIFICATION,
            getBoolean(context, PrefConst.KEY_SHOW_CODE_NOTIFICATION, true),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
            getBoolean(context, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false),
        )
        editor.putString(
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            getString(
                context,
                PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
            ),
        )
        editor.putBoolean(PrefConst.KEY_DEDUPLICATE_SMS, getBoolean(context, PrefConst.KEY_DEDUPLICATE_SMS, true))
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
            getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, true),
        )
        editor.putBoolean(
            PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
            getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, false),
        )
        editor.putString(PrefConst.KEY_HISTORY_LIMIT, getString(context, PrefConst.KEY_HISTORY_LIMIT, "0"))
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_CODE,
            getString(
                context,
                PrefConst.KEY_HISTORY_LIMIT_CODE,
                getString(context, PrefConst.KEY_HISTORY_LIMIT, "0"),
            ),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS,
            getString(
                context,
                PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS,
                getString(context, PrefConst.KEY_HISTORY_LIMIT, "0"),
            ),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY,
            getString(
                context,
                PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY,
                getString(context, PrefConst.KEY_HISTORY_LIMIT, "0"),
            ),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
            getString(
                context,
                PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
                "20",
            ),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_UPDATE_ON_START,
            getBoolean(context, PrefConst.KEY_AUTO_UPDATE_ON_START, true),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
            getBoolean(context, PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY, false),
        )
        editor.putBoolean(
            PrefConst.KEY_LOW_BATTERY_REMINDER_ENABLE,
            getBoolean(context, PrefConst.KEY_LOW_BATTERY_REMINDER_ENABLE, false),
        )
        editor.putInt(
            PrefConst.KEY_LOW_BATTERY_THRESHOLD,
            getInt(context, PrefConst.KEY_LOW_BATTERY_THRESHOLD, PrefConst.LOW_BATTERY_THRESHOLD_DEFAULT),
        )
        editor.putString(
            PrefConst.KEY_LOW_BATTERY_CHANNEL_ID,
            getString(context, PrefConst.KEY_LOW_BATTERY_CHANNEL_ID, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_FULL_BATTERY_REMINDER_ENABLE,
            getBoolean(context, PrefConst.KEY_FULL_BATTERY_REMINDER_ENABLE, false),
        )
        editor.putString(
            PrefConst.KEY_FULL_BATTERY_CHANNEL_ID,
            getString(context, PrefConst.KEY_FULL_BATTERY_CHANNEL_ID, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_CALL_ALERT_LOCAL_ENABLED,
            getBoolean(context, PrefConst.KEY_CALL_ALERT_LOCAL_ENABLED, false),
        )
        editor.putBoolean(
            PrefConst.KEY_CALL_ALERT_FORWARD_ENABLED,
            getBoolean(context, PrefConst.KEY_CALL_ALERT_FORWARD_ENABLED, false),
        )
        editor.putString(
            PrefConst.KEY_CALL_ALERT_CHANNEL_ID,
            getString(context, PrefConst.KEY_CALL_ALERT_CHANNEL_ID, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_VERIFICATION_FEATURES_ENABLED,
            getBoolean(context, PrefConst.KEY_VERIFICATION_FEATURES_ENABLED, true),
        )
        editor.putBoolean(
            PrefConst.KEY_RELAY_FEATURES_ENABLED,
            getBoolean(context, PrefConst.KEY_RELAY_FEATURES_ENABLED, true),
        )
        editor.putBoolean(
            PrefConst.KEY_MSG_TYPE_SMS_CODE_ENABLED,
            getBoolean(context, PrefConst.KEY_MSG_TYPE_SMS_CODE_ENABLED, true),
        )
        editor.putBoolean(
            PrefConst.KEY_MSG_TYPE_SMS_PLAIN_ENABLED,
            getBoolean(context, PrefConst.KEY_MSG_TYPE_SMS_PLAIN_ENABLED, true),
        )
        editor.putBoolean(
            PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
            getBoolean(context, PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED, true),
        )
        editor.putBoolean(
            PrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED,
            getBoolean(context, PrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED, false),
        )
        editor.putBoolean(
            PrefConst.KEY_FORWARD_SMS_CODE_ENABLED,
            getBoolean(context, PrefConst.KEY_FORWARD_SMS_CODE_ENABLED, true),
        )
        editor.putBoolean(
            PrefConst.KEY_FORWARD_SMS_PLAIN_ENABLED,
            getBoolean(context, PrefConst.KEY_FORWARD_SMS_PLAIN_ENABLED, true),
        )
        editor.putBoolean(
            PrefConst.KEY_FORWARD_APP_NOTIFY_ENABLED,
            getBoolean(context, PrefConst.KEY_FORWARD_APP_NOTIFY_ENABLED, true),
        )
        editor.putBoolean(
            PrefConst.KEY_FORWARD_CALL_NOTIFY_ENABLED,
            getBoolean(context, PrefConst.KEY_FORWARD_CALL_NOTIFY_ENABLED, false),
        )
        editor.putBoolean(
            PrefConst.KEY_SMS_KEYWORD_ALERT_ENABLED,
            getBoolean(context, PrefConst.KEY_SMS_KEYWORD_ALERT_ENABLED, false),
        )
        editor.putString(
            PrefConst.KEY_SMS_KEYWORD_ALERT_KEYWORDS,
            getString(context, PrefConst.KEY_SMS_KEYWORD_ALERT_KEYWORDS, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_SMS_KEYWORD_ALERT_NOTIFICATION,
            getBoolean(context, PrefConst.KEY_SMS_KEYWORD_ALERT_NOTIFICATION, true),
        )
        editor.putBoolean(
            PrefConst.KEY_SMS_KEYWORD_ALERT_SOUND,
            getBoolean(context, PrefConst.KEY_SMS_KEYWORD_ALERT_SOUND, true),
        )
        editor.putBoolean(
            PrefConst.KEY_SMS_KEYWORD_ALERT_VIBRATE,
            getBoolean(context, PrefConst.KEY_SMS_KEYWORD_ALERT_VIBRATE, true),
        )
        editor.putBoolean(
            PrefConst.KEY_APP_KEYWORD_ALERT_ENABLED,
            getBoolean(context, PrefConst.KEY_APP_KEYWORD_ALERT_ENABLED, false),
        )
        editor.putString(
            PrefConst.KEY_APP_KEYWORD_ALERT_KEYWORDS,
            getString(context, PrefConst.KEY_APP_KEYWORD_ALERT_KEYWORDS, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_APP_KEYWORD_ALERT_NOTIFICATION,
            getBoolean(context, PrefConst.KEY_APP_KEYWORD_ALERT_NOTIFICATION, true),
        )
        editor.putBoolean(
            PrefConst.KEY_APP_KEYWORD_ALERT_SOUND,
            getBoolean(context, PrefConst.KEY_APP_KEYWORD_ALERT_SOUND, true),
        )
        editor.putBoolean(
            PrefConst.KEY_APP_KEYWORD_ALERT_VIBRATE,
            getBoolean(context, PrefConst.KEY_APP_KEYWORD_ALERT_VIBRATE, true),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
            getBoolean(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, false),
        )
        editor.putInt(
            PrefConst.KEY_HAZE_BLUR_RADIUS,
            getInt(context, PrefConst.KEY_HAZE_BLUR_RADIUS, PrefConst.HAZE_BLUR_RADIUS_DEFAULT),
        )
        editor.putFloat(
            PrefConst.KEY_HAZE_TINT_ALPHA,
            getFloat(context, PrefConst.KEY_HAZE_TINT_ALPHA, PrefConst.HAZE_TINT_ALPHA_DEFAULT),
        )
        editor.putString(
            PrefConst.KEY_FORWARD_COMMON_DEVICE_NAME,
            getString(context, PrefConst.KEY_FORWARD_COMMON_DEVICE_NAME, ""),
        )
        editor.putString(
            PrefConst.KEY_FORWARD_COMMON_TEMPLATE,
            getString(context, PrefConst.KEY_FORWARD_COMMON_TEMPLATE, ""),
        )
        editor.putString(
            PrefConst.KEY_FORWARD_APP_NOTIFY_TEMPLATE,
            getString(context, PrefConst.KEY_FORWARD_APP_NOTIFY_TEMPLATE, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_FORWARD_COMMON_INCLUDE_TIME,
            getBoolean(context, PrefConst.KEY_FORWARD_COMMON_INCLUDE_TIME, false),
        )
        editor.putBoolean(
            PrefConst.KEY_FORWARD_COMMON_INCLUDE_SENDER,
            getBoolean(context, PrefConst.KEY_FORWARD_COMMON_INCLUDE_SENDER, false),
        )
        editor.putBoolean(
            PrefConst.KEY_FORWARD_COMMON_INCLUDE_DEVICE_NAME,
            getBoolean(context, PrefConst.KEY_FORWARD_COMMON_INCLUDE_DEVICE_NAME, true),
        )
        editor.putBoolean(
            PrefConst.KEY_WEBUI_ENABLE,
            getBoolean(context, PrefConst.KEY_WEBUI_ENABLE, true),
        )
        editor.putBoolean(
            PrefConst.KEY_WEBUI_LAN_ACCESS,
            getBoolean(context, PrefConst.KEY_WEBUI_LAN_ACCESS, false),
        )
        editor.putString(
            PrefConst.KEY_WEBUI_PORT,
            getString(context, PrefConst.KEY_WEBUI_PORT, PrefConst.KEY_WEBUI_PORT_DEFAULT),
        )
        editor.putString(
            PrefConst.KEY_WEBUI_USERNAME,
            getString(context, PrefConst.KEY_WEBUI_USERNAME, PrefConst.KEY_WEBUI_USERNAME_DEFAULT),
        )
        editor.putString(
            PrefConst.KEY_WEBUI_PASSWORD,
            getString(context, PrefConst.KEY_WEBUI_PASSWORD, ""),
        )
        editor.putString(
            PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION,
            getString(
                context,
                PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION,
                PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION_DEFAULT,
            ),
        )
        editor.putString(
            PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_PASS,
            getString(context, PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_PASS, ""),
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
        editor.apply()
        ensureSharedPrefsReadable(context)
        syncToRemotePrefs(context)
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
                PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB,
                getInt(
                    context,
                    PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB,
                    PrefConst.RUNTIME_LOG_FILE_SIZE_MB_DEFAULT,
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
                getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.SMSCODE_KEYWORDS_DEFAULT),
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
                getString(context, PrefConst.KEY_SMSCODE_REGEX, PrefConst.SMSCODE_REGEX_DEFAULT),
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
            editor.putString(
                PrefConst.KEY_WEBUI_PORT,
                getString(context, PrefConst.KEY_WEBUI_PORT, PrefConst.KEY_WEBUI_PORT_DEFAULT),
            )
            editor.putString(
                PrefConst.KEY_WEBUI_USERNAME,
                getString(context, PrefConst.KEY_WEBUI_USERNAME, PrefConst.KEY_WEBUI_USERNAME_DEFAULT),
            )
            editor.putString(
                PrefConst.KEY_WEBUI_PASSWORD,
                getString(context, PrefConst.KEY_WEBUI_PASSWORD, ""),
            )
            editor.putString(
                PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION,
                getString(
                    context,
                    PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION,
                    PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION_DEFAULT,
                ),
            )
            editor.putString(
                PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_PASS,
                getString(context, PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_PASS, ""),
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
            val committed = editor.commit()
            if (!committed) {
                remoteSyncPending = true
                XLog.w("RemotePrefs sync failed: commit returned false")
            } else {
                remoteSyncPending = false
                remoteSyncPendingLogged = false
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

    private suspend fun recoverIntFromSharedPrefs(
        context: Context,
        key: String,
        defaultValue: Int,
    ): Int {
        val sharedPrefs = getSharedPrefs(context)
        sharedPrefs.getString(key, null)
            ?.trim()
            ?.toIntOrNull()
            ?.let { recovered ->
                XLog.w(
                    "Recovered int preference from string key=%s value=%d",
                    key,
                    recovered,
                )
                runCatching { setInt(context, key, recovered) }
                    .onFailure { error ->
                        XLog.w(
                            "Failed to rewrite recovered int preference key=%s err=%s",
                            key,
                            error.message ?: error.javaClass.simpleName,
                        )
                    }
                return recovered
            }
        XLog.w(
            "SharedPreferences int type mismatch key=%s fallback=%d",
            key,
            defaultValue,
        )
        return defaultValue
    }
}
