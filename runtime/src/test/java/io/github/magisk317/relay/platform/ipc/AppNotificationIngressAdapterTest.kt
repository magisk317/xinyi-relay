package io.github.magisk317.relay.platform.ipc

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk

class AppNotificationIngressAdapterTest {

    @Test
    fun toPayload_returnsNullForSelfPackage() {
        val context = mockk<Context>()
        val sbn = mockk<StatusBarNotification>()
        every { context.packageName } returns "io.github.magisk317.xinyi.relay"
        every { sbn.packageName } returns "io.github.magisk317.xinyi.relay"

        assertNull(AppNotificationIngressAdapter.toPayload(context, sbn))
    }

    @Test
    fun shouldSkipNotification_skipsForegroundAndGroupSummary() {
        val foreground = Notification().apply {
            flags = Notification.FLAG_FOREGROUND_SERVICE
        }
        assertTrue(AppNotificationIngressAdapter.shouldSkipNotification(foreground))

        val summary = Notification().apply {
            flags = Notification.FLAG_GROUP_SUMMARY
        }
        assertTrue(AppNotificationIngressAdapter.shouldSkipNotification(summary))
    }
}
