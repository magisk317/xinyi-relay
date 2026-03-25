package io.github.magisk317.relay.common.constant

enum class PrefValueType {
    BOOLEAN,
    INT,
    FLOAT,
    STRING,
}

object PrefRestoreTypeRegistry {
    val BOOLEAN_KEYS: Set<String> = setOf(
        PrefConst.KEY_ENABLE,
        PrefConst.KEY_HIDE_LAUNCHER_ICON,
        PrefConst.KEY_SHOW_LAUNCHER_ICON,
        PrefConst.KEY_SETTINGS_ACCORDION_MODE,
        PrefConst.KEY_SHOW_TOAST,
        PrefConst.KEY_COPY_TO_CLIPBOARD,
        PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
        PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
        PrefConst.KEY_BLOCK_SMS,
        PrefConst.KEY_DEDUPLICATE_SMS,
        PrefConst.KEY_ENABLE_SMS_BLACKLIST,
        PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
        PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
        PrefConst.KEY_SHOW_CODE_NOTIFICATION,
        PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
        PrefConst.KEY_ENABLE_CODE_RECORDS,
        PrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
        PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS,
        PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY,
        PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY,
        PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE,
        PrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK,
        PrefConst.KEY_INTERNAL_ROOT_DB_BASELINE_INITED,
        PrefConst.KEY_MARK_AS_READ,
        PrefConst.KEY_DELETE_SMS,
        PrefConst.KEY_FORCE_STOP_RECOVERY,
        PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
        PrefConst.KEY_VERBOSE_LOG_MODE,
        PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
        PrefConst.KEY_AUTO_UPDATE_ON_START,
        PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
        PrefConst.KEY_LOW_BATTERY_REMINDER_ENABLE,
        PrefConst.KEY_FULL_BATTERY_REMINDER_ENABLE,
        PrefConst.KEY_CALL_ALERT_LOCAL_ENABLED,
        PrefConst.KEY_CALL_ALERT_FORWARD_ENABLED,
        PrefConst.KEY_MSG_TYPE_SMS_CODE_ENABLED,
        PrefConst.KEY_MSG_TYPE_SMS_PLAIN_ENABLED,
        PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
        PrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED,
        PrefConst.KEY_FORWARD_SMS_CODE_ENABLED,
        PrefConst.KEY_FORWARD_SMS_PLAIN_ENABLED,
        PrefConst.KEY_FORWARD_APP_NOTIFY_ENABLED,
        PrefConst.KEY_FORWARD_CALL_NOTIFY_ENABLED,
        PrefConst.KEY_VERIFICATION_FEATURES_ENABLED,
        PrefConst.KEY_RELAY_FEATURES_ENABLED,
        PrefConst.KEY_SMS_KEYWORD_ALERT_ENABLED,
        PrefConst.KEY_SMS_KEYWORD_ALERT_NOTIFICATION,
        PrefConst.KEY_SMS_KEYWORD_ALERT_SOUND,
        PrefConst.KEY_SMS_KEYWORD_ALERT_VIBRATE,
        PrefConst.KEY_APP_KEYWORD_ALERT_ENABLED,
        PrefConst.KEY_APP_KEYWORD_ALERT_NOTIFICATION,
        PrefConst.KEY_APP_KEYWORD_ALERT_SOUND,
        PrefConst.KEY_APP_KEYWORD_ALERT_VIBRATE,
        PrefConst.KEY_FORWARD_COMMON_INCLUDE_TIME,
        PrefConst.KEY_FORWARD_COMMON_INCLUDE_SENDER,
        PrefConst.KEY_FORWARD_COMMON_INCLUDE_DEVICE_NAME,
        PrefConst.KEY_WEBUI_ENABLE,
        PrefConst.KEY_WEBUI_LAN_ACCESS,
        PrefConst.KEY_PRIVACY_POLICY_ACCEPTED,
        PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN,
        // Compatibility keys for imports from older backups.
        PrefConst.KEY_ENABLE_SMS_BLOCK,
        PrefConst.KEY_ENABLE_NOTIFICATION_FORWARD,
    )

    val INT_KEYS: Set<String> = setOf(
        PrefConst.KEY_CHOOSE_THEME,
        PrefConst.KEY_HAZE_BLUR_RADIUS,
        PrefConst.KEY_LOW_BATTERY_THRESHOLD,
        "local_version_code",
    )

    val FLOAT_KEYS: Set<String> = setOf(
        PrefConst.KEY_HAZE_TINT_ALPHA,
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
