package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.common.constant.NotificationConst
import io.github.magisk317.relay.common.utils.NotificationUtils

object XpNotificationBridge {
    const val CHANNEL_ID_RELAY_NOTIFICATION: String = NotificationConst.CHANNEL_ID_RELAY_NOTIFICATION
    const val CHANNEL_ID_SMSCODE_CONFLICT: String = NotificationConst.CHANNEL_ID_SMSCODE_CONFLICT
    const val GROUP_KEY_RELAY_NOTIFICATION: String = NotificationConst.GROUP_KEY_RELAY_NOTIFICATION
    const val NOTIFICATION_ID_SMSCODE_CONFLICT: Int = NotificationConst.NOTIFICATION_ID_SMSCODE_CONFLICT

    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        NotificationUtils.createNotificationChannel(context, channelId, channelName, importance)
    }
}
