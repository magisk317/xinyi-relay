package io.github.magisk317.relay.contract.constant

object NotificationConst {

    const val CHANNEL_ID_FOREGROUND_SERVICE = "foreground_service"

    const val CHANNEL_ID_RELAY_NOTIFICATION = "relay_notification"

    /**
     * Rotated channel id for the phone-owned fallback, used when the user switched the primary
     * channel off. Android does not let an app raise the importance of an existing channel, so a
     * fresh id is the only way to recover from `IMPORTANCE_NONE` without user action.
     */
    const val CHANNEL_ID_RELAY_NOTIFICATION_FALLBACK = "relay_notification_fallback"
    const val CHANNEL_ID_SMSCODE_CONFLICT = "smscode_conflict"
    const val GROUP_KEY_RELAY_NOTIFICATION = "group_key_relay_notification"
    const val NOTIFICATION_ID_SMSCODE_CONFLICT = 0x73636f6e

    const val CHANNEL_ID_SPECIAL_ALERT_SILENT = "special_alert_silent"
    const val CHANNEL_ID_SPECIAL_ALERT_VIBRATE = "special_alert_vibrate"
    const val CHANNEL_ID_SPECIAL_ALERT_SOUND = "special_alert_sound"
    const val CHANNEL_ID_SPECIAL_ALERT_SOUND_VIBRATE = "special_alert_sound_vibrate"
}
