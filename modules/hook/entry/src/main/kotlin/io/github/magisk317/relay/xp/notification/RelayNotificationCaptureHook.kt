package io.github.magisk317.relay.xp.notification

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.magisk317.forward.capture.CallNotificationClassifier
import io.github.magisk317.forward.contract.ForwardBroadcastContract
import io.github.magisk317.forward.contract.constant.ForwardCallType
import io.github.magisk317.forward.contract.ForwardMessageType
import io.github.magisk317.forward.contract.ForwardPayload
import io.github.magisk317.forward.contract.ForwardSource
import io.github.magisk317.relay.xp.hook.helper.XinyiHookHostCapabilities
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import io.github.magisk317.smscode.xposed.hook.telephony.SmsHookCapabilities
import io.github.magisk317.xposed.capture.CapturedNotification
import io.github.magisk317.xposed.capture.DefaultNotificationCapturePolicy
import io.github.magisk317.xposed.capture.NotificationCaptureEmit
import io.github.magisk317.xposed.capture.NotificationCaptureHook
import io.github.magisk317.xposed.capture.NotificationCapturePolicy
import io.github.magisk317.xposed.capture.NotificationContentResolver
import io.github.magisk317.xposed.capability.runtime.CoreRuntime
import io.github.magisk317.xposed.capability.utils.NotificationLogSanitizer
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.runNonFatalCatching

/**
 * Product wiring for the shared notification capture pipeline: resolves the
 * relay endpoint, classifies the capture (call / telephony SMS / app code),
 * applies the skip policy and hands the payload to the shared dispatcher.
 */
class RelayNotificationCaptureHook(
    private val hostCapabilities: SmsHookCapabilities,
) : NotificationCaptureHook(hostCapabilities) {

    private val policy: NotificationCapturePolicy = RelayNotificationCapturePolicy()
    private val lastCallSourceReportAtMs = ReportingThrottle(CALL_SOURCE_REPORT_INTERVAL_MS)
    private val lastNotifyReportAtMs = ReportingThrottle(NOTIFY_REPORT_INTERVAL_MS)

    override fun onBlankContent(capture: CapturedNotification, notification: android.app.Notification) {
        if (capture.packageName != WECHAT_PACKAGE) return
        XLog.w(
            message = TAG + ": wechat blank content dropped. flags=0x%s category=%s",
            Integer.toHexString(notification.flags),
            notification.category ?: "<null>",
        )
    }

    override fun onNotificationCaptured(
        capture: CapturedNotification,
        systemContext: Context,
        emit: NotificationCaptureEmit,
    ) {
        val endpoint = RelayForwardDispatchConfig.endpointResolver.resolve(systemContext) ?: run {
            XLog.w(TAG + ": failed to resolve module endpoint")
            emit.skip(reason = "no_endpoint", targetPackage = capture.packageName)
            return
        }
        if (capture.packageName == endpoint.packageName) {
            XLog.d(TAG + ": skip self notification. pkg=" + capture.packageName)
            emit.skip(reason = "self", targetPackage = capture.packageName)
            return
        }

        reportCallNotificationSource(systemContext, capture)
        val classification = CallNotificationClassifier.classify(
            packageName = capture.packageName,
            category = capture.category,
            channelId = capture.channelId,
            title = capture.title,
            body = capture.body,
            tickerText = capture.tickerText,
            expandedText = capture.expandedText,
        )
        val route = resolveRoute(capture, classification)
        val skipReason = policy.skipReason(capture, isPriorityRoute = classification != null)
        if (skipReason != null) {
            XLog.d(
                message = TAG + ": skip by policy. pkg=%s reason=%s channel=%s flags=0x%s category=%s",
                capture.packageName,
                skipReason,
                capture.channelId.ifBlank { "<empty>" },
                Integer.toHexString(capture.flags),
                capture.category ?: "<null>",
            )
            reportNotifyEvent(
                systemContext = systemContext,
                capture = capture,
                msgType = route.messageType,
                skipReason = skipReason,
            )
            emit.skip(reason = skipReason, targetPackage = capture.packageName, msgType = route.messageType)
            return
        }

        val eventId = ForwardBroadcastContract.buildEventId(EVENT_PREFIX, capture.packageName)
        XLog.w(
            message = DIAG_ENTRY_FORMAT,
            eventId,
            capture.packageName,
            route.messageType,
        )
        val displaySender = if (classification != null) {
            CallNotificationClassifier.resolveDisplaySender(
                title = capture.title,
                body = capture.body,
                expandedText = capture.expandedText,
                tickerText = capture.tickerText,
            )
        } else {
            capture.title
        }
        val companyLabel = when (route.messageType) {
            ForwardMessageType.CALL_NOTIFICATION -> classification?.label?.ifBlank { CallNotificationClassifier.DEFAULT_CALL_LABEL }
            ForwardMessageType.SMS -> SmsCodeNotificationAnalyzer.resolveCompanyLabel(
                title = capture.title,
                body = capture.body,
                expandedText = capture.expandedText,
                tickerText = capture.tickerText,
                fallbackLabel = resolveAppName(systemContext, capture.packageName),
            )
            else -> resolveAppName(systemContext, capture.packageName)
        }
        val payload = ForwardPayload(
            eventId = eventId,
            sender = displaySender,
            body = capture.body,
            date = System.currentTimeMillis(),
            company = companyLabel,
            smsCode = route.smsCode?.takeIf { it.isNotBlank() },
            packageName = capture.packageName,
            notifyChannelId = capture.channelId,
            messageType = route.messageType,
            forwardSource = ForwardSource.NMS_HOOK,
            callType = classification?.callType ?: ForwardCallType.UNKNOWN,
            callStage = classification?.stage.orEmpty(),
        )
        val intent = Intent(ForwardBroadcastContract.forwardAction(CoreRuntime.access.actionNamespace)).apply {
            setClassName(endpoint.packageName, endpoint.receiverClassName)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val token = RelayForwardDispatchConfig.dispatcher.readIpcToken()
        if (token.isBlank()) {
            XLog.w(
                TAG + ": ipc token empty, continue forwarding with receiver-side bypass. pkg=" + capture.packageName,
            )
        }
        ForwardBroadcastContract.write(intent, payload, token.ifBlank { null })

        val sensitiveDebugLog = CoreRuntime.access.debug
        XLog.i(
            message = TAG + " intercepted: pkg=%s event=%s type=%s callType=%d title=%s body=%s",
            capture.packageName,
            eventId,
            route.messageType,
            payload.callType,
            NotificationLogSanitizer.formatTitle(capture.title, sensitiveDebugLog),
            NotificationLogSanitizer.formatBody(capture.body, sensitiveDebugLog),
        )
        reportNotifyEvent(
            systemContext = systemContext,
            capture = capture,
            msgType = route.messageType,
            skipReason = "",
        )
        RelayForwardDispatchConfig.dispatcher.dispatch(
            systemContext = systemContext,
            intent = intent,
            sourcePackage = capture.packageName,
            eventId = eventId,
            endpoint = endpoint,
        )
        emit.forwarded(targetPackage = capture.packageName, msgType = route.messageType)
    }

    private fun resolveRoute(
        capture: CapturedNotification,
        classification: CallNotificationClassifier.CallClassification?,
    ): Route {
        classification?.let { return Route(messageType = ForwardMessageType.CALL_NOTIFICATION) }
        if (SmsCodeNotificationAnalyzer.isTelephonySmsPackage(capture.packageName)) {
            val content = NotificationContentResolver.combinedContent(
                title = capture.title,
                body = capture.body,
                tickerText = capture.tickerText,
                expandedText = capture.expandedText,
            )
            val smsCode = SmsCodeNotificationAnalyzer.extractCode(
                content = content,
                source = SmsCodeParseSource(
                    packageName = capture.packageName,
                    sender = capture.title,
                    title = capture.title,
                    channelId = capture.channelId,
                    sourceKind = SmsCodeParseSourceKind.SMS,
                ),
            )
            if (smsCode.isNotBlank()) return Route(messageType = ForwardMessageType.SMS, smsCode = smsCode)
        }
        val content = NotificationContentResolver.combinedContent(
            title = capture.title,
            body = capture.body,
            tickerText = capture.tickerText,
            expandedText = capture.expandedText,
        )
        val smsCode = SmsCodeNotificationAnalyzer.extractCode(
            content = content,
            source = SmsCodeParseSource(
                packageName = capture.packageName,
                sender = capture.title,
                title = capture.title,
                channelId = capture.channelId,
                sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
            ),
        )
        if (smsCode.isNotBlank()) return Route(messageType = ForwardMessageType.APP_NOTIFICATION, smsCode = smsCode)
        return Route(messageType = ForwardMessageType.APP_NOTIFICATION)
    }

    private fun resolveAppName(systemContext: Context, packageName: String): String {
        return runNonFatalCatching {
            val pm = systemContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    private fun reportNotifyEvent(
        systemContext: Context,
        capture: CapturedNotification,
        msgType: String,
        skipReason: String,
    ) {
        if (!lastNotifyReportAtMs.tryAcquire()) return
        runNonFatalCatching {
            hostCapabilities.recordNotifyEvent(
                context = systemContext,
                source = "nms",
                packageName = capture.packageName,
                channelId = capture.channelId,
                msgType = msgType,
                skipReason = skipReason,
                verboseLogging = false,
            )
        }
    }

    private fun reportCallNotificationSource(
        systemContext: Context,
        capture: CapturedNotification,
    ) {
        if (!lastCallSourceReportAtMs.tryAcquire(forPackage = capture.packageName)) return
        val looksLikeCall = CallNotificationClassifier.looksLikeCallSource(
            packageName = capture.packageName,
            category = capture.category,
            channelId = capture.channelId,
            title = capture.title,
            body = capture.body,
            tickerText = capture.tickerText,
            expandedText = capture.expandedText,
        )
        if (!looksLikeCall) return
        runNonFatalCatching {
            hostCapabilities.recordCallNotificationSource(
                context = systemContext,
                packageName = capture.packageName,
                channelId = capture.channelId,
                category = capture.category.orEmpty(),
                verboseLogging = false,
            )
        }
    }

    /** Sliding-interval throttle; the call-source variant also keys on the package. */
    private class ReportingThrottle(private val intervalMs: Long) {
        @Volatile
        private var lastAtMs: Long = 0L

        @Volatile
        private var lastPackage: String = ""

        fun tryAcquire(forPackage: String? = null): Boolean {
            val now = System.currentTimeMillis()
            if (forPackage != null && forPackage == lastPackage && now - lastAtMs < intervalMs) return false
            if (forPackage == null && now - lastAtMs < intervalMs) return false
            lastAtMs = now
            lastPackage = forPackage ?: ""
            return true
        }
    }

    private data class Route(
        val messageType: String,
        val smsCode: String? = null,
    )

    private class RelayNotificationCapturePolicy : DefaultNotificationCapturePolicy() {
        override fun isGroupSummaryBypassed(packageName: String): Boolean = packageName == WECHAT_PACKAGE
    }

    private companion object {
        const val TAG = "RelayNotificationCaptureHook"
        const val EVENT_PREFIX = "nms"
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val CALL_SOURCE_REPORT_INTERVAL_MS = 10_000L
        const val NOTIFY_REPORT_INTERVAL_MS = 1_000L
        const val DIAG_ENTRY_FORMAT = "Diag nms_hook entry: event_id=%s pkg=%s route=%s"
    }
}
