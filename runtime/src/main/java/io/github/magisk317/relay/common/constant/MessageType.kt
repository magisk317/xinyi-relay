package io.github.magisk317.relay.common.constant

enum class MessageType(
    val prefKey: String,
    val runtimeType: String,
) {
    SMS_CODE(
        prefKey = PrefConst.KEY_MSG_TYPE_SMS_CODE_ENABLED,
        runtimeType = "sms",
    ),
    SMS_PLAIN(
        prefKey = PrefConst.KEY_MSG_TYPE_SMS_PLAIN_ENABLED,
        runtimeType = "sms",
    ),
    APP_NOTIFY(
        prefKey = PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
        runtimeType = "app_notify",
    ),
    CALL_NOTIFY(
        prefKey = PrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED,
        runtimeType = "call_notify",
    ),
}
