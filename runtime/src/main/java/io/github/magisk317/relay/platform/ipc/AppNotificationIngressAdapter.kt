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
        val expandedText = resolveExpandedText(notification)
        val tickerText = notification.tickerText?.toString() ?: ""
        val notifyChannelId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notification.channelId.orEmpty()
        } else {
            ""
        }
        if (shouldSkipRelayOwnedTelephonyNotification(packageName, notifyChannelId)) {
            XLog.d(
                "Notification ingress skipped: pkg=%s reason=channel_relay_notification channel=%s",
                packageName,
                notifyChannelId.ifBlank { "<empty>" },
            )
            return null
        }
        val body = resolveNotificationBody(text, expandedText, tickerText)

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
        val normalizedChannel = notifyChannelId.trim().lowercase()
        val normalizedText = buildString {
            append(title.trim())
            append('\n')
            append(body.trim())
        }.lowercase()

        return when {
            (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 -> "foreground_service"
            (flags and Notification.FLAG_ONGOING_EVENT) != 0 -> "ongoing_event"
            notification.category == Notification.CATEGORY_SERVICE -> "category_service"
            (flags and Notification.FLAG_GROUP_SUMMARY) != 0 -> "group_summary"
            normalizedChannel == "voicemail" || normalizedChannel == "voice_mail" -> "channel_voicemail"
            normalizedChannel.contains("foreground_service") ||
                normalizedChannel.contains("foregroundservice") -> "channel_foreground_service"
            normalizedChannel.contains("fgs") -> "channel_fgs"
            normalizedChannel.contains("low_importance_service") -> "channel_low_importance_service"
            normalizedChannel.contains("service_channel") ||
                normalizedChannel.contains("servicechannel") -> "channel_service"
            normalizedChannel.contains(".hide") ||
                normalizedChannel.endsWith("_hide") ||
                normalizedChannel.contains("_hide_") -> "channel_hidden"
            normalizedChannel.contains("silent") -> "channel_silent"
            normalizedText.contains("正在运行") -> "content_running"
            normalizedText.contains("前台服务") ||
                normalizedText.contains("foreground service") -> "content_foreground_service"
            normalizedText.contains("正在检查应用更新") ||
                normalizedText.contains("checking app updates") -> "content_checking_updates"
            else -> null
        }
    }

    internal fun resolveNotificationBody(
        text: String,
        expandedText: String,
        tickerText: String,
    ): String {
        val normalizedText = text.trim()
        val normalizedExpanded = expandedText.trim()
        val normalizedTicker = tickerText.trim()
        return when {
            shouldPreferExpandedText(normalizedText, normalizedExpanded) -> normalizedExpanded
            normalizedText.isNotEmpty() -> normalizedText
            normalizedExpanded.isNotEmpty() -> normalizedExpanded
            else -> normalizedTicker
        }
    }

    private fun resolveExpandedText(notification: Notification): String {
        val extras = notification.extras
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("\n")
            .orEmpty()
        return listOf(bigText, lines, subText)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    private fun shouldPreferExpandedText(
        text: String,
        expandedText: String,
    ): Boolean {
        if (expandedText.isBlank()) return false
        if (text.isBlank()) return true
        if (looksTruncated(text) && expandedText.length >= text.length) return true
        val comparableText = stripEdgeEllipsis(text)
        if (comparableText.isNotEmpty() && expandedText.contains(comparableText) && expandedText.length > comparableText.length) {
            return true
        }
        return expandedText.length >= text.length + 8
    }

    private fun looksTruncated(text: String): Boolean {
        val normalized = text.trim()
        return normalized.startsWith("...") ||
            normalized.startsWith("…") ||
            normalized.endsWith("...") ||
            normalized.endsWith("…")
    }

    private fun stripEdgeEllipsis(text: String): String {
        return text.trim()
            .removePrefix("...")
            .removePrefix("…")
            .removeSuffix("...")
            .removeSuffix("…")
            .trim()
    }

    internal fun shouldSkipRelayOwnedTelephonyNotification(
        packageName: String,
        notifyChannelId: String,
    ): Boolean {
        val normalizedPackage = packageName.trim().lowercase()
        val normalizedChannel = notifyChannelId.trim().lowercase()
        if (normalizedChannel != RELAY_NOTIFICATION_CHANNEL_ID) return false
        return normalizedPackage in TELEPHONY_SMS_PACKAGE_ALLOWLIST || normalizedPackage.contains("telephony")
    }

    private const val RELAY_NOTIFICATION_CHANNEL_ID = "relay_notification"

    private val TELEPHONY_SMS_PACKAGE_ALLOWLIST = setOf(
        "com.android.phone",
        "com.android.providers.telephony",
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
    )
}
