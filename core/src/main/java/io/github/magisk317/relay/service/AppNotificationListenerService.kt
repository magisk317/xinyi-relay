package io.github.magisk317.relay.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.feature.reminder.SpecialAlertCoordinator
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import kotlinx.coroutines.runBlocking

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

        // TODO: check configuration rules (blacklist/whitelist) for notifications.
        val eventId = buildEventId(packageName)
        val forwardIntent = Intent(PrefConst.ACTION_FORWARD_SMS)
        forwardIntent.setClassName(applicationContext.packageName, FORWARD_RECEIVER_CLASS_NAME)
        forwardIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        forwardIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        forwardIntent.putExtra("sender", title)
        forwardIntent.putExtra("body", body)
        forwardIntent.putExtra("date", sbn.postTime)
        forwardIntent.putExtra("packageName", packageName)
        forwardIntent.putExtra("notify_channel_id", notifyChannelId)
        forwardIntent.putExtra("msgType", "app_notify")
        forwardIntent.putExtra("forward_source", "nls")
        forwardIntent.putExtra("event_id", eventId)
        
        // Resolve App Name
        val pm = applicationContext.packageManager
        val appName = try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            XLog.w("Notification app label not found for pkg=%s err=%s", packageName, e.message ?: "unknown")
            packageName
        }
        forwardIntent.putExtra("company", appName)

        SpecialAlertCoordinator.notifyForEvent(
            context = applicationContext,
            event = RelayEvent(
                messageType = MessageType.APP_NOTIFY,
                sourceType = "nls",
                sender = title,
                body = body,
                timestamp = sbn.postTime,
                packageName = packageName,
                notifyChannelId = notifyChannelId,
                companyOrAppName = appName,
                smsCode = null,
                callType = 0,
                callStage = "",
                simSlot = -1,
                subId = 0,
            ),
            traceId = eventId,
        )

        val token = runBlocking {
            val runtimeGraph = RuntimeGraph.from(applicationContext)
            RuntimeSettingsCache.getString(
                key = PrefConst.KEY_IPC_TOKEN,
                defaultValue = "",
            ) { key, defaultValue ->
                runtimeGraph.preferenceDataSource.getString(key, defaultValue)
            }
        }
        forwardIntent.putExtra("ipc_token", token)

        XLog.i("Notification intercepted: pkg=%s event=%s title=%s body=%s", packageName, eventId, title, body)
        if (BuildConfig.DEBUG) {
            sendOrderedBroadcast(
                forwardIntent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: Intent?) {
                        XLog.i(
                            "NLS ordered ack pkg=%s event=%s resultCode=%d resultData=%s extras=%s",
                            packageName,
                            eventId,
                            resultCode,
                            resultData ?: "<null>",
                            getResultExtras(true)?.toString() ?: "<null>",
                        )
                    }
                },
                null,
                0,
                null,
                null,
            )
        } else {
            sendBroadcast(forwardIntent)
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

    private fun buildEventId(packageName: String): String {
        val now = System.currentTimeMillis().toString(36)
        val suffix = kotlin.math.abs((packageName + now).hashCode()).toString(36)
        return "nls_${now}_$suffix"
    }

    companion object {
        private const val FORWARD_RECEIVER_CLASS_NAME = "io.github.magisk317.relay.platform.ipc.ForwardReceiver"
    }
}
