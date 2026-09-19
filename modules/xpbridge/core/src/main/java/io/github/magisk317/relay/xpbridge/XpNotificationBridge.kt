package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.constant.NotificationConst
import io.github.magisk317.relay.contract.notification.NoopRelayNotificationPlatformBridge
import io.github.magisk317.relay.contract.notification.RelayNotificationPlatformBridge
import io.github.magisk317.smscode.runtime.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.smscode.runtime.contract.notification.NotificationPlatformBridge

object XpNotificationBridge : NotificationPlatformBridge {
    const val CHANNEL_ID_RELAY_NOTIFICATION: String = NotificationConst.CHANNEL_ID_RELAY_NOTIFICATION
    const val CHANNEL_ID_RELAY_NOTIFICATION_FALLBACK: String =
        NotificationConst.CHANNEL_ID_RELAY_NOTIFICATION_FALLBACK
    const val CHANNEL_ID_SMSCODE_CONFLICT: String = NotificationConst.CHANNEL_ID_SMSCODE_CONFLICT
    const val GROUP_KEY_RELAY_NOTIFICATION: String = NotificationConst.GROUP_KEY_RELAY_NOTIFICATION
    const val NOTIFICATION_ID_SMSCODE_CONFLICT: Int = NotificationConst.NOTIFICATION_ID_SMSCODE_CONFLICT

    @Volatile
    private var platformBridge: RelayNotificationPlatformBridge = NoopRelayNotificationPlatformBridge

    fun installPlatformBridge(bridge: RelayNotificationPlatformBridge?) {
        platformBridge = bridge ?: NoopRelayNotificationPlatformBridge
    }

    override fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        platformBridge.createNotificationChannel(context, channelId, channelName, importance)
    }

    override fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics =
        platformBridge.inspectDelivery(context, channelId)

    override fun hasPostNotificationsPermission(context: Context): Boolean {
        return platformBridge.hasPostNotificationsPermission(context)
    }
}
