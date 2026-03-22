package io.github.magisk317.relay.platform.ipc

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import io.github.magisk317.relay.common.utils.XLog

object AppNotificationIngressAdapter {
    fun toPayload(
        context: Context,
        sbn: StatusBarNotification,
    ): ForwardBroadcastPayload? {
        val packageName = sbn.packageName
        if (packageName == context.packageName || packageName == "android") {
            return null
        }

        val notification = sbn.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val tickerText = notification.tickerText?.toString() ?: ""
        val notifyChannelId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notification.channelId.orEmpty()
        } else {
            ""
        }
        val body = if (text.isNotEmpty()) text else tickerText

        if (title.isBlank() && body.isBlank()) return null
        if (shouldSkipNotification(notification)) return null

        val appName = try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            XLog.w("Notification app label not found for pkg=%s err=%s", packageName, e.message ?: "unknown")
            packageName
        }

        return ForwardPayloadFactory.appNotificationPayload(
            packageName = packageName,
            title = title,
            body = body,
            timestamp = sbn.postTime,
            appName = appName,
            notifyChannelId = notifyChannelId,
        )
    }

    internal fun shouldSkipNotification(notification: Notification): Boolean {
        val flags = notification.flags
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return true
        }
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return true
        }
        if (notification.category == Notification.CATEGORY_SERVICE) {
            return true
        }
        return (flags and Notification.FLAG_GROUP_SUMMARY) != 0
    }
}
