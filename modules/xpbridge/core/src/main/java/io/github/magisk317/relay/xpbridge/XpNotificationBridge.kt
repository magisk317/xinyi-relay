package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.constant.NotificationConst
import io.github.magisk317.relay.contract.notification.NoopNotificationPlatformBridge
import io.github.magisk317.relay.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.relay.contract.notification.NotificationPlatformBridge

object XpNotificationBridge {
    const val CHANNEL_ID_RELAY_NOTIFICATION: String = NotificationConst.CHANNEL_ID_RELAY_NOTIFICATION
    const val CHANNEL_ID_SMSCODE_CONFLICT: String = NotificationConst.CHANNEL_ID_SMSCODE_CONFLICT
    const val GROUP_KEY_RELAY_NOTIFICATION: String = NotificationConst.GROUP_KEY_RELAY_NOTIFICATION
    const val NOTIFICATION_ID_SMSCODE_CONFLICT: Int = NotificationConst.NOTIFICATION_ID_SMSCODE_CONFLICT

    @Volatile
    private var platformBridge: NotificationPlatformBridge = NoopNotificationPlatformBridge

    fun installPlatformBridge(bridge: NotificationPlatformBridge?) {
        platformBridge = bridge ?: NoopNotificationPlatformBridge
    }

    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        platformBridge.createNotificationChannel(context, channelId, channelName, importance)
    }

    fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics =
        platformBridge.inspectDelivery(context, channelId)

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return platformBridge.hasPostNotificationsPermission(context)
    }
}
