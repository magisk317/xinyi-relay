package io.github.magisk317.relay.contract.constant

enum class MessageType(
    val prefKey: String,
    val runtimeType: String,
) {
    SMS_CODE(
        prefKey = "pref_msg_type_sms_code_enabled",
        runtimeType = "sms",
    ),
    SMS_PLAIN(
        prefKey = "pref_msg_type_sms_plain_enabled",
        runtimeType = "sms",
    ),
    APP_NOTIFY(
        prefKey = "pref_msg_type_app_notify_enabled",
        runtimeType = "app_notify",
    ),
    CALL_NOTIFY(
        prefKey = "pref_msg_type_call_notify_enabled",
        runtimeType = "call_notify",
    ),
}
