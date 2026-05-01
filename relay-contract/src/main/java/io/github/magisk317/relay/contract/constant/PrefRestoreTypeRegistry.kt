package io.github.magisk317.relay.contract.constant

enum class PrefValueType {
    BOOLEAN,
    INT,
    FLOAT,
    STRING,
}

object PrefRestoreTypeRegistry {
    val BOOLEAN_KEYS: Set<String> = setOf(
        RelayPrefConst.KEY_ENABLE,
        RelayPrefConst.KEY_HIDE_LAUNCHER_ICON,
        RelayPrefConst.KEY_SHOW_LAUNCHER_ICON,
        RelayPrefConst.KEY_SETTINGS_ACCORDION_MODE,
        RelayPrefConst.KEY_SHOW_TOAST,
        RelayPrefConst.KEY_COPY_TO_CLIPBOARD,
        RelayPrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
        RelayPrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
        RelayPrefConst.KEY_BLOCK_SMS,
        RelayPrefConst.KEY_DEDUPLICATE_SMS,
        RelayPrefConst.KEY_ENABLE_SMS_BLACKLIST,
        RelayPrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
        RelayPrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
        RelayPrefConst.KEY_SHOW_CODE_NOTIFICATION,
        RelayPrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
        RelayPrefConst.KEY_ENABLE_CODE_RECORDS,
        RelayPrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
        RelayPrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS,
        RelayPrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY,
        RelayPrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY,
        RelayPrefConst.KEY_ROOT_DB_CATCHUP_ENABLE,
        RelayPrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK,
        RelayPrefConst.KEY_INTERNAL_ROOT_DB_BASELINE_INITED,
        RelayPrefConst.KEY_MARK_AS_READ,
        RelayPrefConst.KEY_DELETE_SMS,
        RelayPrefConst.KEY_FORCE_STOP_RECOVERY,
        RelayPrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
        RelayPrefConst.KEY_VERBOSE_LOG_MODE,
        RelayPrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
        RelayPrefConst.KEY_ENABLE_ANALYTICS,
        RelayPrefConst.KEY_AUTO_UPDATE_ON_START,
        RelayPrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
        RelayPrefConst.KEY_LOW_BATTERY_REMINDER_ENABLE,
        RelayPrefConst.KEY_FULL_BATTERY_REMINDER_ENABLE,
        RelayPrefConst.KEY_CALL_ALERT_LOCAL_ENABLED,
        RelayPrefConst.KEY_CALL_ALERT_FORWARD_ENABLED,
        RelayPrefConst.KEY_MSG_TYPE_SMS_CODE_ENABLED,
        RelayPrefConst.KEY_MSG_TYPE_SMS_PLAIN_ENABLED,
        RelayPrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
        RelayPrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED,
        RelayPrefConst.KEY_FORWARD_SMS_CODE_ENABLED,
        RelayPrefConst.KEY_FORWARD_SMS_PLAIN_ENABLED,
        RelayPrefConst.KEY_FORWARD_APP_NOTIFY_ENABLED,
        RelayPrefConst.KEY_FORWARD_CALL_NOTIFY_ENABLED,
        RelayPrefConst.KEY_FORWARD_CALL_NOTIFY_FINAL_ENABLED,
        RelayPrefConst.KEY_VERIFICATION_FEATURES_ENABLED,
        RelayPrefConst.KEY_RELAY_FEATURES_ENABLED,
        RelayPrefConst.KEY_ENABLE_CALL_RELAY,
        RelayPrefConst.KEY_ENABLE_SMS_RELAY,
        RelayPrefConst.KEY_ENABLE_APP_RELAY,
        RelayPrefConst.KEY_RELAY_BY_WIFI,
        RelayPrefConst.KEY_RELAY_BY_DATA,
        RelayPrefConst.KEY_RELAY_KEYWORDS_CASE_INSENSITIVE,
        RelayPrefConst.KEY_SMS_KEYWORD_ALERT_ENABLED,
        RelayPrefConst.KEY_SMS_KEYWORD_ALERT_NOTIFICATION,
        RelayPrefConst.KEY_SMS_KEYWORD_ALERT_SOUND,
        RelayPrefConst.KEY_SMS_KEYWORD_ALERT_VIBRATE,
        RelayPrefConst.KEY_APP_KEYWORD_ALERT_ENABLED,
        RelayPrefConst.KEY_APP_KEYWORD_ALERT_NOTIFICATION,
        RelayPrefConst.KEY_APP_KEYWORD_ALERT_SOUND,
        RelayPrefConst.KEY_APP_KEYWORD_ALERT_VIBRATE,
        RelayPrefConst.KEY_FORWARD_COMMON_INCLUDE_TIME,
        RelayPrefConst.KEY_FORWARD_COMMON_INCLUDE_SENDER,
        RelayPrefConst.KEY_FORWARD_COMMON_INCLUDE_DEVICE_NAME,
        RelayPrefConst.KEY_PRIVACY_POLICY_ACCEPTED,
        RelayPrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN,
        RelayPrefConst.KEY_ENABLE_SMS_BLOCK,
        RelayPrefConst.KEY_ENABLE_NOTIFICATION_FORWARD,
    )

    val INT_KEYS: Set<String> = setOf(
        RelayPrefConst.KEY_CHOOSE_THEME,
        RelayPrefConst.KEY_HAZE_BLUR_RADIUS,
        RelayPrefConst.KEY_LOW_BATTERY_THRESHOLD,
        RelayPrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB,
        "local_version_code",
    )

    val FLOAT_KEYS: Set<String> = setOf(
        RelayPrefConst.KEY_HAZE_TINT_ALPHA,
    )

    fun typeOf(key: String): PrefValueType {
        return when {
            BOOLEAN_KEYS.contains(key) -> PrefValueType.BOOLEAN
            INT_KEYS.contains(key) -> PrefValueType.INT
            FLOAT_KEYS.contains(key) -> PrefValueType.FLOAT
            else -> PrefValueType.STRING
        }
    }
}
