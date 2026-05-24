package io.github.magisk317.relay.android.platform.notification

import android.content.Context
import io.github.magisk317.relay.android.common.utils.NotificationUtils
import io.github.magisk317.relay.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.relay.contract.notification.NotificationPlatformBridge

object AndroidNotificationPlatformBridge : NotificationPlatformBridge {
    override fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        NotificationUtils.createNotificationChannel(context, channelId, channelName, importance)
    }

    override fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics {
        return NotificationUtils.inspectDelivery(context, channelId)
    }

    override fun hasPostNotificationsPermission(context: Context): Boolean {
        return NotificationUtils.hasPostNotificationsPermission(context)
    }
}
