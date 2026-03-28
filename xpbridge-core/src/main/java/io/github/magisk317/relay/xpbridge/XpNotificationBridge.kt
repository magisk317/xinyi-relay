package io.github.magisk317.relay.xpbridge

import android.app.NotificationManager
import android.content.Context
import io.github.magisk317.relay.common.constant.NotificationConst
import io.github.magisk317.relay.common.utils.NotificationUtils

object XpNotificationBridge {
    const val CHANNEL_ID_RELAY_NOTIFICATION: String = NotificationConst.CHANNEL_ID_RELAY_NOTIFICATION
    const val CHANNEL_ID_SMSCODE_CONFLICT: String = NotificationConst.CHANNEL_ID_SMSCODE_CONFLICT
    const val GROUP_KEY_RELAY_NOTIFICATION: String = NotificationConst.GROUP_KEY_RELAY_NOTIFICATION
    const val NOTIFICATION_ID_SMSCODE_CONFLICT: Int = NotificationConst.NOTIFICATION_ID_SMSCODE_CONFLICT

    data class DeliveryDiagnostics(
        val notificationsEnabled: Boolean,
        val postNotificationsGranted: Boolean,
        val channelImportance: Int?,
    ) {
        val canPost: Boolean
            get() = notificationsEnabled &&
                postNotificationsGranted &&
                channelImportance != NotificationManager.IMPORTANCE_NONE

        fun summary(): String {
            return "enabled=$notificationsEnabled permission=$postNotificationsGranted channel=${NotificationUtils.importanceLabel(channelImportance)}"
        }
    }

    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        NotificationUtils.createNotificationChannel(context, channelId, channelName, importance)
    }

    fun inspectDelivery(context: Context, channelId: String): DeliveryDiagnostics {
        val diagnostics = NotificationUtils.inspectDelivery(context, channelId)
        return DeliveryDiagnostics(
            notificationsEnabled = diagnostics.notificationsEnabled,
            postNotificationsGranted = diagnostics.postNotificationsGranted,
            channelImportance = diagnostics.channelImportance,
        )
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return NotificationUtils.hasPostNotificationsPermission(context)
    }
}
