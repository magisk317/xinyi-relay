package io.github.magisk317.relay.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.feature.reminder.SpecialAlertCoordinator
import io.github.magisk317.relay.platform.ipc.AppNotificationIngressAdapter
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        XLog.i(
            "Notification intercepted: pkg=%s event=%s title=%s body=%s",
            payload.packageName,
            payload.eventId,
            payload.sender.orEmpty(),
            payload.body.orEmpty(),
        )

        serviceScope.launch {
            val wakeLock = acquireWakeLock()
            try {
                SpecialAlertCoordinator.notifyForEvent(
                    context = applicationContext,
                    event = payload.toRelayEvent(),
                    traceId = payload.eventId,
                )
                // Sync trigger is handled by EventPipeline.finally via messageSyncTrigger callback.
                // No duplicate scheduleMessageTriggeredSync call needed here.

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
            } finally {
                wakeLock?.release()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        return runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xinyi-relay:notification-dispatch")
            wl.acquire(30_000L) // 30 second timeout as safety net
            wl
        }.onFailure { e ->
            XLog.w("Failed to acquire wake lock: %s", e.message)
        }.getOrNull()
    }
}
