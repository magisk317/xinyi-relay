package io.github.magisk317.relay.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.feature.reminder.SpecialAlertCoordinator
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher
import io.github.magisk317.relay.platform.ipc.ForwardPayloadFactory

class AppNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        XLog.i("AppNotificationListenerService connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        XLog.w("AppNotificationListenerService disconnected, requestRebind")
        runCatching {
            requestRebind(ComponentName(this, AppNotificationListenerService::class.java))
        }.onFailure {
            XLog.e("AppNotificationListenerService requestRebind failed", it)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        // The original `if (sbn == null) return` is removed because sbn is now non-nullable.

        val packageName = sbn.packageName
        // Do not forward our own notifications or system notifications
        if (packageName == applicationContext.packageName || packageName == "android") {
            return
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

        if (title.isBlank() && body.isBlank()) return
        if (shouldSkipNotification(notification)) return

        // Resolve App Name
        val pm = applicationContext.packageManager
        val appName = try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            XLog.w("Notification app label not found for pkg=%s err=%s", packageName, e.message ?: "unknown")
            packageName
        }

        val payload = ForwardPayloadFactory.appNotificationPayload(
            packageName = packageName,
            title = title,
            body = body,
            timestamp = sbn.postTime,
            appName = appName,
            notifyChannelId = notifyChannelId,
        )

        SpecialAlertCoordinator.notifyForEvent(
            context = applicationContext,
            event = payload.toRelayEvent(),
            traceId = payload.eventId,
        )

        XLog.i(
            "Notification intercepted: pkg=%s event=%s title=%s body=%s",
            packageName,
            payload.eventId,
            title,
            body,
        )
        if (BuildConfig.DEBUG) {
            ForwardBroadcastDispatcher.dispatchFromHost(
                context = applicationContext,
                payload = payload,
            ) { ack ->
                XLog.i(
                    "NLS ordered ack pkg=%s event=%s resultCode=%d resultData=%s extras=%s",
                    packageName,
                    payload.eventId,
                    ack.resultCode,
                    ack.resultData ?: "<null>",
                    ack.resultExtras?.toString() ?: "<null>",
                )
            }
        } else {
            ForwardBroadcastDispatcher.dispatchFromHost(
                context = applicationContext,
                payload = payload,
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    private fun shouldSkipNotification(notification: Notification): Boolean {
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
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        return isGroupSummary
    }

}
