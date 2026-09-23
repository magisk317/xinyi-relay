package io.github.magisk317.relay.android.prefs

import android.content.Context
import android.content.SharedPreferences
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.smscode.rule.constant.SmsCodeConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesHooks
import io.github.magisk317.xposed.logging.XLog

/**
 * xinyi-relay's extension points for the shared preference store.
 *
 * The mirror list is the set of keys a non-app process must observe; it is the
 * only part of the store that is repository specific. Boolean coercion gates
 * the sensitive debug log switch on builds that cannot support it.
 */
object RelayPreferenceHooks : AppPreferencesHooks {
    override fun coerceBoolean(key: String, value: Boolean): Boolean {
        if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !PrefsReader.isSensitiveDebugLogSupported()) {
            return false
        }
        return value
    }

    override suspend fun publishRemoteEntries(context: Context, editor: SharedPreferences.Editor) {
            editor.putBoolean(PrefConst.KEY_ENABLE, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE, true))
            editor.putBoolean(
                PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                AppPreferencesDataStore.getBoolean(
                    context,
                    PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                    PrefConst.DEFAULT_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                ),
            )
            editor.putString(
                PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_SETTINGS_ACCORDION_MODE,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SETTINGS_ACCORDION_MODE, true),
            )
            editor.putBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_VERBOSE_LOG_MODE, false))
            editor.putBoolean(
                PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false),
            )
            editor.putInt(
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                AppPreferencesDataStore.getInt(
                    context,
                    PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                    PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
                ),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true),
            )
            editor.putString(
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT),
            )
            editor.putString(
                PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
                AppPreferencesDataStore.getString(
                    context,
                    PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
                    PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
                ),
            )
            editor.putBoolean(PrefConst.KEY_SHOW_TOAST, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SHOW_TOAST, true))
            editor.putString(
                PrefConst.KEY_SMSCODE_KEYWORDS,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, SmsCodeConst.VERIFICATION_KEYWORDS_REGEX),
            )
            editor.putBoolean(PrefConst.KEY_MARK_AS_READ, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_MARK_AS_READ, false))
            editor.putBoolean(PrefConst.KEY_DELETE_SMS, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_DELETE_SMS, false))
            editor.putBoolean(
                PrefConst.KEY_COPY_TO_CLIPBOARD,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, false),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, true),
            )
            editor.putBoolean(PrefConst.KEY_BLOCK_SMS, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_BLOCK_SMS, false))
            editor.putBoolean(
                PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SHOW_CODE_NOTIFICATION, true),
            )
            editor.putString(
                PrefConst.KEY_CODE_NOTIFICATION_OWNER,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_CODE_NOTIFICATION_OWNER, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, true),
            )
            editor.putString(
                PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                AppPreferencesDataStore.getString(
                    context,
                    PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                    PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
                ),
            )
            editor.putString(
                PrefConst.KEY_CODE_NOTIFICATION_RETENTION_TIME,
                AppPreferencesDataStore.getString(
                    context,
                    PrefConst.KEY_CODE_NOTIFICATION_RETENTION_TIME,
                    PrefConst.KEY_CODE_NOTIFICATION_RETENTION_TIME_DEFAULT,
                ),
            )
            editor.putString(
                PrefConst.KEY_APP_NOTIFY_CODE_RETENTION_TIME,
                AppPreferencesDataStore.getString(
                    context,
                    PrefConst.KEY_APP_NOTIFY_CODE_RETENTION_TIME,
                    PrefConst.KEY_APP_NOTIFY_CODE_RETENTION_TIME_DEFAULT,
                ),
            )
            editor.putString(
                PrefConst.KEY_CALL_NOTIFY_CODE_RETENTION_TIME,
                AppPreferencesDataStore.getString(
                    context,
                    PrefConst.KEY_CALL_NOTIFY_CODE_RETENTION_TIME,
                    PrefConst.KEY_CALL_NOTIFY_CODE_RETENTION_TIME_DEFAULT,
                ),
            )
            editor.putBoolean(
                PrefConst.KEY_DEDUPLICATE_SMS,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_DEDUPLICATE_SMS, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_SMS_BLACKLIST,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_SMS_BLACKLIST, false),
            )
            editor.putString(
                PrefConst.KEY_SMS_BLACKLIST_NUMBERS,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, ""),
            )
            editor.putString(
                PrefConst.KEY_SMS_BLACKLIST_PREFIXES,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, ""),
            )
            editor.putString(
                PrefConst.KEY_SMS_BLACKLIST_REGEX,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_REGEX, ""),
            )
            editor.putString(
                PrefConst.KEY_SMS_BLACKLIST_CONTENT,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_CONTENT, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, false),
            )
            editor.putBoolean(
                PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, false),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_CALL_RELAY,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CALL_RELAY, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_SMS_RELAY,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_SMS_RELAY, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_APP_RELAY,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_APP_RELAY, true),
            )
            editor.putBoolean(
                PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, false),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_KEYWORDS_EXCLUDE,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMSCODE_KEYWORDS_EXCLUDE, ""),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_REGEX,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMSCODE_REGEX, SmsCodeConst.VERIFICATION_KEYWORDS_REGEX),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_REGEX_EXCLUDE,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMSCODE_REGEX_EXCLUDE, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_RELAY_BY_WIFI,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_RELAY_BY_WIFI, false),
            )
            editor.putBoolean(
                PrefConst.KEY_RELAY_BY_DATA,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_RELAY_BY_DATA, false),
            )
            editor.putString(
                PrefConst.KEY_RELAY_TEST,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_RELAY_TEST, ""),
            )
            editor.putString(
                PrefConst.KEY_RELAY_KEYWORDS,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_RELAY_KEYWORDS, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_RELAY_KEYWORDS_CASE_INSENSITIVE,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_RELAY_KEYWORDS_CASE_INSENSITIVE, true),
            )
            editor.putString(
                PrefConst.KEY_RELAY_KEYWORDS_REGEX,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_RELAY_KEYWORDS_REGEX, ""),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_KEYWORDS_BLACKLIST,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMSCODE_KEYWORDS_BLACKLIST, ""),
            )
            editor.putString(
                PrefConst.KEY_SMSCODE_KEYWORDS_WHITELIST,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_SMSCODE_KEYWORDS_WHITELIST, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE, false),
            )
            editor.putString(
                PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN, "5"),
            )
            editor.putBoolean(
                PrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK, false),
            )
            editor.putBoolean(
                PrefConst.KEY_INTERNAL_ROOT_DB_BASELINE_INITED,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_INTERNAL_ROOT_DB_BASELINE_INITED, false),
            )
            editor.putString(
                PrefConst.KEY_INTERNAL_ROOT_DB_LAST_SMS_ID,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_SMS_ID, "0"),
            )
            editor.putString(
                PrefConst.KEY_INTERNAL_ROOT_DB_LAST_CALL_ID,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_CALL_ID, "0"),
            )
            editor.putString(
                PrefConst.KEY_IPC_TOKEN,
                AppPreferencesDataStore.getString(context, PrefConst.KEY_IPC_TOKEN, ""),
            )
            editor.putBoolean(
                PrefConst.KEY_FORCE_STOP_RECOVERY,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_FORCE_STOP_RECOVERY, false),
            )
            editor.putBoolean(
                PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
                AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE, false),
            )
    }

    override suspend fun verifyRemoteSync(context: Context, prefs: SharedPreferences) {
        val token = prefs.getString(PrefConst.KEY_IPC_TOKEN, null)
        if (token.isNullOrBlank()) {
            XLog.w("RemotePrefs token verification: ipc_token is blank after sync")
            return
        }
        val verifyToken = PrefsReader.verifyTokenReadable(context)
        if (verifyToken == "remote_libxposed") {
            XLog.d("RemotePrefs token verification: source=%s", verifyToken)
        } else {
            XLog.w("RemotePrefs token verification source unexpected: source=%s", verifyToken)
        }
    }
}
