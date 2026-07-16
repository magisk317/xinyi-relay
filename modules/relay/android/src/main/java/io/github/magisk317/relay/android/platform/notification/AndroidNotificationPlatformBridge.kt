package io.github.magisk317.relay.android.platform.notification

import android.content.Context
import io.github.magisk317.relay.contract.notification.RelayNotificationPlatformBridge
import io.github.magisk317.smscode.runtime.common.notification.AndroidNotificationPlatformBridge as SharedAndroidNotificationPlatformBridge
import io.github.magisk317.smscode.runtime.contract.notification.NotificationDeliveryDiagnostics

object AndroidNotificationPlatformBridge : RelayNotificationPlatformBridge {
    private val platform = SharedAndroidNotificationPlatformBridge(recoverDeletedChannels = true)

    override fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        platform.createNotificationChannel(context, channelId, channelName, importance)
    }

    override fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics {
        return platform.inspectDelivery(context, channelId)
    }

    override fun hasPostNotificationsPermission(context: Context): Boolean {
        return platform.hasPostNotificationsPermission(context)
    }
}
