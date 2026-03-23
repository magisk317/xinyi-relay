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
        val skipReason = resolveSkipReason(
            notification = notification,
            title = title,
            body = body,
            notifyChannelId = notifyChannelId,
        )
        if (skipReason != null) {
            XLog.d(
                "Notification ingress skipped: pkg=%s reason=%s channel=%s",
                packageName,
                skipReason,
                notifyChannelId.ifBlank { "<empty>" },
            )
            return null
        }

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

    internal fun shouldSkipNotification(
        notification: Notification,
        title: String = "",
        body: String = "",
        notifyChannelId: String = "",
    ): Boolean = resolveSkipReason(
        notification = notification,
        title = title,
        body = body,
        notifyChannelId = notifyChannelId,
    ) != null

    internal fun resolveSkipReason(
        notification: Notification,
        title: String,
        body: String,
        notifyChannelId: String,
    ): String? {
        val flags = notification.flags
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return "foreground_service"
        }
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return "ongoing_event"
        }
        if (notification.category == Notification.CATEGORY_SERVICE) {
            return "category_service"
        }
        if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return "group_summary"
        }

        val normalizedChannel = notifyChannelId.trim().lowercase()
        if (normalizedChannel == "voicemail" || normalizedChannel == "voice_mail") {
            return "channel_voicemail"
        }
        if (normalizedChannel.contains("foreground_service") || normalizedChannel.contains("foregroundservice")) {
            return "channel_foreground_service"
        }
        if (normalizedChannel.contains("fgs")) {
            return "channel_fgs"
        }
        if (normalizedChannel.contains("low_importance_service")) {
            return "channel_low_importance_service"
        }
        if (normalizedChannel.contains("service_channel") || normalizedChannel.contains("servicechannel")) {
            return "channel_service"
        }
        if (normalizedChannel.contains(".hide") || normalizedChannel.endsWith("_hide") || normalizedChannel.contains("_hide_")) {
            return "channel_hidden"
        }
        if (normalizedChannel.contains("silent")) {
            return "channel_silent"
        }

        val normalizedText = buildString {
            append(title.trim())
            append('\n')
            append(body.trim())
        }.lowercase()
        if (normalizedText.contains("正在运行")) {
            return "content_running"
        }
        if (normalizedText.contains("前台服务") || normalizedText.contains("foreground service")) {
            return "content_foreground_service"
        }
        if (normalizedText.contains("正在检查应用更新") || normalizedText.contains("checking app updates")) {
            return "content_checking_updates"
        }
        return null
    }
}
