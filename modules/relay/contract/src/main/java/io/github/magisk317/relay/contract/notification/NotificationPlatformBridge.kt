package io.github.magisk317.relay.contract.notification

import android.content.Context
import io.github.magisk317.smscode.runtime.contract.notification.NoopNotificationPlatformBridge as CoreNoopNotificationPlatformBridge
import io.github.magisk317.smscode.runtime.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.smscode.runtime.contract.notification.NotificationPlatformBridge

/**
 * xinyi-relay's notification platform bridge.
 * Extends smscode-core's [NotificationPlatformBridge] interface.
 */
interface RelayNotificationPlatformBridge : NotificationPlatformBridge

/**
 * No-op implementation for xinyi-relay.
 * Forwards every member to smscode-core's [CoreNoopNotificationPlatformBridge];
 * kept as a distinct object only so it satisfies the [RelayNotificationPlatformBridge]
 * marker type used by the bridge install points.
 */
object NoopRelayNotificationPlatformBridge : RelayNotificationPlatformBridge {

    private val delegate: NotificationPlatformBridge = CoreNoopNotificationPlatformBridge

    override fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) = delegate.createNotificationChannel(context, channelId, channelName, importance)

    override fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics =
        delegate.inspectDelivery(context, channelId)

    override fun hasPostNotificationsPermission(context: Context): Boolean =
        delegate.hasPostNotificationsPermission(context)
}

// Type aliases for backward compatibility
typealias NotificationDeliveryDiagnostics = NotificationDeliveryDiagnostics
typealias NotificationPlatformBridge = NotificationPlatformBridge
typealias NoopNotificationPlatformBridge = NoopRelayNotificationPlatformBridge
