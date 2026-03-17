package io.github.magisk317.relay.xp.hook.notification

import android.app.Notification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationManagerHookPolicyTest {
    private val hook = NotificationManagerHook()

    @Test
    fun `call notification with service flags is not skipped`() {
        val notification = Notification().apply {
            category = Notification.CATEGORY_CALL
            flags = Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_ONGOING_EVENT
        }
        val route = resolveRoute(
            packageName = "com.android.dialer",
            notification = notification,
            title = "未接电话",
            body = "10086",
            tickerText = "",
            expandedText = "",
            notifyChannelId = "",
        )

        val skipReason = getSkipReason(
            packageName = "com.android.dialer",
            notification = notification,
            route = route,
        )

        assertNull(skipReason)
    }

    @Test
    fun `non call notification keeps foreground_service skip policy`() {
        val notification = Notification().apply {
            category = Notification.CATEGORY_SERVICE
            flags = Notification.FLAG_FOREGROUND_SERVICE
        }
        val route = resolveRoute(
            packageName = "com.follow.clash",
            notification = notification,
            title = "running",
            body = "vpn service",
            tickerText = "",
            expandedText = "",
            notifyChannelId = "",
        )

        val skipReason = getSkipReason(
            packageName = "com.follow.clash",
            notification = notification,
            route = route,
        )

        assertEquals("foreground_service", skipReason)
    }

    private fun resolveRoute(
        packageName: String,
        notification: Notification,
        title: String,
        body: String,
        tickerText: String,
        expandedText: String,
        notifyChannelId: String,
    ): Any {
        val method = hook.javaClass.getDeclaredMethod(
            "resolveNotifyRoute",
            String::class.java,
            Notification::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return requireNotNull(
            method.invoke(hook, packageName, notification, title, body, tickerText, expandedText, notifyChannelId),
        )
    }

    private fun getSkipReason(
        packageName: String,
        notification: Notification,
        route: Any,
    ): String? {
        val notifyRouteClass = route.javaClass
        val method = hook.javaClass.getDeclaredMethod(
            "getSkipReason",
            String::class.java,
            Notification::class.java,
            notifyRouteClass,
        )
        method.isAccessible = true
        return method.invoke(hook, packageName, notification, route) as String?
    }
}
