package io.github.magisk317.relay.platform.ipc

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import io.github.magisk317.xposed.capture.CapturedNotification
import io.github.magisk317.xposed.capture.DefaultNotificationCapturePolicy
import io.github.magisk317.xposed.capture.NotificationContentResolver
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.android.sms.SmsCodeUtils
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.forward.runtime.ingress.ForwardBroadcastPayload

object AppNotificationIngressAdapter {
    private const val NANOS_PER_MILLI = 1_000_000L

    // Shared capture skip policy; xinyi adds no priority routes here, so every
    // notification goes through the non-priority guard set.
    private val capturePolicy = DefaultNotificationCapturePolicy()

    fun toPayload(
        context: Context,
        sbn: StatusBarNotification,
    ): ForwardBroadcastPayload? {
        val startedAt = System.nanoTime()
        val packageName = sbn.packageName.orEmpty()
        if (packageName.isBlank()) {
            emitAppNotification(
                result = "skip",
                reason = "blank_package",
                durationMs = elapsedMs(startedAt),
            )
            return null
        }
        val notification = sbn.notification ?: run {
            emitAppNotification(
                result = "skip",
                reason = "missing_notification",
                durationMs = elapsedMs(startedAt),
                targetPackage = packageName,
            )
            return null
        }
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val expandedText = NotificationContentResolver.resolveExpandedText(notification)
        val tickerText = notification.tickerText?.toString().orEmpty()
        val body = NotificationContentResolver.resolvePreferredBody(
            text = text,
            expandedText = expandedText,
            tickerText = tickerText,
        )
        val notifyChannelId = notification.channelId.orEmpty()
        if (title.isBlank() && body.isBlank()) {
            emitAppNotification(
                result = "skip",
                reason = "empty_content",
                durationMs = elapsedMs(startedAt),
                targetPackage = packageName,
            )
            return null
        }

        val skipReason = resolveSkipReason(
            notification = notification,
            title = title,
            body = body,
            notifyChannelId = notifyChannelId,
        )
        if (skipReason != null) {
            XLog.d(
                message = "Notification ingress skipped: pkg=%s reason=%s channel=%s",
                packageName,
                skipReason,
                notifyChannelId.ifBlank { "<empty>" },
            )
            emitAppNotification(
                result = "skip",
                reason = skipReason,
                durationMs = elapsedMs(startedAt),
                targetPackage = packageName,
            )
            return null
        }

        val appName = try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            XLog.w(message = "Notification app label not found for pkg=%s err=%s", packageName, e.message ?: "unknown")
            packageName
        }

        emitAppNotification(
            result = "ok",
            reason = "accepted",
            durationMs = elapsedMs(startedAt),
            targetPackage = packageName,
            bodyLength = body.length,
        )
        return ForwardPayloadFactory.appNotificationPayload(
            packageName = packageName,
            title = title,
            body = body,
            timestamp = sbn.postTime,
            appName = appName,
            notifyChannelId = notifyChannelId,
        )
    }

    suspend fun toPayloadWithParsedSmsCode(
        context: Context,
        sbn: StatusBarNotification,
    ): ForwardBroadcastPayload? {
        val payload = toPayload(context, sbn) ?: return null
        val notification = sbn.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val expandedText = NotificationContentResolver.resolveExpandedText(notification)
        val tickerText = notification.tickerText?.toString() ?: ""
        val content = buildNotificationParseContent(
            title = title,
            text = text,
            body = payload.body.orEmpty(),
            expandedText = expandedText,
            tickerText = tickerText,
        )
        val smsCode = parseNotificationSmsCode(
            context = context,
            packageName = payload.packageName.orEmpty(),
            title = title,
            notifyChannelId = payload.notifyChannelId,
            content = content,
        )
        return payload.copy(smsCode = smsCode)
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
        return capturePolicy.skipReason(
            capture = CapturedNotification(
                packageName = "",
                title = title,
                body = body,
                tickerText = "",
                expandedText = "",
                channelId = notifyChannelId,
                category = notification.category,
                flags = notification.flags,
            ),
            isPriorityRoute = false,
        )
    }

    internal fun buildNotificationParseContent(
        title: String,
        text: String,
        body: String,
        expandedText: String,
        tickerText: String,
    ): String {
        return listOf(title, text, body, expandedText, tickerText)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
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

    internal fun shouldIgnoreSourcePackage(
        hostPackageName: String,
        sourcePackageName: String,
    ): Boolean {
        return sourcePackageName == hostPackageName || sourcePackageName == "android"
    }

    private suspend fun parseNotificationSmsCode(
        context: Context,
        packageName: String,
        title: String,
        notifyChannelId: String,
        content: String,
    ): String? {
        if (content.isBlank()) return null
        return runCatching {
            SmsCodeUtils.parseSmsCodeResultIfExists(
                context = context,
                content = content,
                source = SmsCodeParseSource(
                    packageName = packageName,
                    sender = title,
                    title = title,
                    channelId = notifyChannelId,
                    sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
                ),
            ).code.trim().takeIf { it.isNotEmpty() }
        }.onFailure { error ->
            XLog.w(
                message = "Notification smsCode parse failed: pkg=%s err=%s",
                packageName.ifBlank { "<empty>" },
                error.message ?: error.javaClass.simpleName,
            )
        }.getOrNull()
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

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)

    private fun emitAppNotification(
        result: String,
        reason: String,
        durationMs: Long,
        targetPackage: String = "",
        bodyLength: Int? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "app",
            "stage" to "app_notification",
            "reason" to reason,
        )
        if (targetPackage.isNotBlank()) {
            attrs["target_package"] = targetPackage
        }
        if (bodyLength != null) {
            attrs["body_length"] = bodyLength.toString()
        }
        MagiskOtel.event(name = "sms.ingest", attributes = attrs, statusOk = true)
    }
}
