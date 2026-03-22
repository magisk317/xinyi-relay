package io.github.magisk317.relay.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.feature.reminder.SpecialAlertCoordinator
import io.github.magisk317.relay.platform.ipc.AppNotificationIngressAdapter
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher

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
        val payload = AppNotificationIngressAdapter.toPayload(applicationContext, sbn) ?: return

        SpecialAlertCoordinator.notifyForEvent(
            context = applicationContext,
            event = payload.toRelayEvent(),
            traceId = payload.eventId,
        )

        XLog.i(
            "Notification intercepted: pkg=%s event=%s title=%s body=%s",
            payload.packageName,
            payload.eventId,
            payload.sender.orEmpty(),
            payload.body.orEmpty(),
        )
        if (BuildConfig.DEBUG) {
            ForwardBroadcastDispatcher.dispatchFromHost(
                context = applicationContext,
                payload = payload,
            ) { ack ->
                XLog.i(
                    "NLS ordered ack pkg=%s event=%s resultCode=%d resultData=%s extras=%s",
                    payload.packageName.orEmpty(),
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
}
