package io.github.magisk317.relay.contract.notification

import android.app.NotificationManager
import android.content.Context

data class NotificationDeliveryDiagnostics(
    val notificationsEnabled: Boolean,
    val postNotificationsGranted: Boolean,
    val channelImportance: Int?,
) {
    val canPost: Boolean
        get() = notificationsEnabled &&
            postNotificationsGranted &&
            channelImportance != NotificationManager.IMPORTANCE_NONE

    fun summary(): String {
        return "enabled=$notificationsEnabled permission=$postNotificationsGranted channel=${NotificationImportanceLabel.label(channelImportance)}"
    }
}

interface NotificationPlatformBridge {
    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    )

    fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics

    fun hasPostNotificationsPermission(context: Context): Boolean
}

object NoopNotificationPlatformBridge : NotificationPlatformBridge {
    override fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) = Unit

    override fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics {
        return NotificationDeliveryDiagnostics(
            notificationsEnabled = false,
            postNotificationsGranted = false,
            channelImportance = null,
        )
    }

    override fun hasPostNotificationsPermission(context: Context): Boolean = false
}

object NotificationImportanceLabel {
    fun label(importance: Int?): String {
        return when (importance) {
            null -> "missing"
            NotificationManager.IMPORTANCE_NONE -> "none"
            NotificationManager.IMPORTANCE_MIN -> "min"
            NotificationManager.IMPORTANCE_LOW -> "low"
            NotificationManager.IMPORTANCE_DEFAULT -> "default"
            NotificationManager.IMPORTANCE_HIGH -> "high"
            NotificationManager.IMPORTANCE_MAX -> "max"
            else -> importance.toString()
        }
    }
}
