package io.github.magisk317.relay.contract.notification

import android.content.Context
import io.github.magisk317.smscode.runtime.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.smscode.runtime.contract.notification.NotificationPlatformBridge

/**
 * xinyi-relay's notification platform bridge.
 * Extends smscode-core's [NotificationPlatformBridge] interface.
 */
interface RelayNotificationPlatformBridge : NotificationPlatformBridge

/**
 * No-op implementation for xinyi-relay.
 */
object NoopRelayNotificationPlatformBridge : RelayNotificationPlatformBridge {
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

// Type aliases for backward compatibility
typealias NotificationDeliveryDiagnostics = NotificationDeliveryDiagnostics
typealias NotificationPlatformBridge = NotificationPlatformBridge
typealias NoopNotificationPlatformBridge = NoopRelayNotificationPlatformBridge
