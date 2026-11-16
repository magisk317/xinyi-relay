package io.github.magisk317.relay.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.magisk317.relay.android.sms.SmsCodeUtils as RelaySmsCodeUtils
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.verification.CodeNotificationReadCoordinator
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.mobile.core.BuildConfig
import io.github.magisk317.relay.feature.reminder.SpecialAlertCoordinator
import io.github.magisk317.relay.feature.mode.NonRootRuntimeState
import io.github.magisk317.relay.platform.ipc.AppNotificationIngressAdapter
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        NonRootRuntimeState.onListenerConnected()
        XLog.i("AppNotificationListenerService connected")
        emitNotify(
            result = "ok",
            reason = "listener_connected",
            stage = "lifecycle",
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NonRootRuntimeState.onListenerDisconnected()
        XLog.w("AppNotificationListenerService disconnected, requestRebind")
        val rebound = runCatching {
            requestRebind(ComponentName(this, AppNotificationListenerService::class.java))
            true
        }.onFailure {
            XLog.e(message = "AppNotificationListenerService requestRebind failed", it)
        }.getOrDefault(false)
        emitNotify(
            result = if (rebound) "ok" else "error",
            reason = if (rebound) "listener_disconnected_rebind" else "listener_disconnected_rebind_failed",
            stage = "lifecycle",
            statusOk = rebound,
            errorClass = if (rebound) null else "rebind_failed",
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        serviceScope.launch {
            // Mark-as-read runs on the raw sbn BEFORE ingress filtering: the adapter may skip
            // or reclassify telephony-posted notifications, but the notification-side backup
            // must see every SMS-app notification regardless.
            runCatching { maybeMarkSmsNotificationRead(applicationContext, sbn) }
                .onFailure { error ->
                    XLog.w(
                        message = "NLS: notification mark-as-read failed pkg=%s err=%s",
                        sbn.packageName,
                        error.message ?: error.javaClass.simpleName,
                    )
                }

            val startedAt = System.nanoTime()
            val payload = AppNotificationIngressAdapter.toPayloadWithParsedSmsCode(applicationContext, sbn)
                ?: return@launch

            XLog.i(
                message = "Notification intercepted: pkg=%s event=%s title=%s body=%s codePresent=%s",
                payload.packageName,
                payload.eventId,
                payload.sender.orEmpty(),
                payload.body.orEmpty(),
                !payload.smsCode.isNullOrBlank(),
            )

            runCatching {
                RuntimeStateStore.recordNotifyEvent(
                    context = applicationContext,
                    source = "nls",
                    packageName = payload.packageName.orEmpty(),
                    channelId = payload.notifyChannelId.orEmpty(),
                    msgType = "app_notify",
                    skipReason = "",
                )
            }.onFailure { error ->
                XLog.w(
                    message = "NLS: notify diagnostics write failed pkg=%s err=%s",
                    payload.packageName,
                    error.message ?: error.javaClass.simpleName,
                )
            }

            val wakeLock = acquireWakeLock()
            try {
                SpecialAlertCoordinator.notifyForEvent(
                    context = applicationContext,
                    event = payload.toRelayEvent(),
                    traceId = payload.eventId,
                )

                if (BuildConfig.DEBUG) {
                    ForwardBroadcastDispatcher.dispatchFromHost(
                        context = applicationContext,
                        payload = payload,
                    ) { ack ->
                        XLog.i(
                            message = "NLS ordered ack pkg=%s event=%s resultCode=%d resultData=%s extras=%s",
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
                emitNotify(
                    result = "ok",
                    reason = "dispatched",
                    stage = "posted",
                    targetPackage = payload.packageName.orEmpty(),
                    codePresent = !payload.smsCode.isNullOrBlank(),
                    eventIdPresent = payload.eventId.isNotBlank(),
                    durationMs = elapsedMs(startedAt),
                )
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                emitNotify(
                    result = "error",
                    reason = "dispatch_failed",
                    stage = "posted",
                    targetPackage = payload.packageName.orEmpty(),
                    codePresent = !payload.smsCode.isNullOrBlank(),
                    eventIdPresent = payload.eventId.isNotBlank(),
                    durationMs = elapsedMs(startedAt),
                    statusOk = false,
                    errorClass = error.javaClass.simpleName,
                )
                throw error
            } finally {
                wakeLock?.release()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    override fun onDestroy() {
        NonRootRuntimeState.onListenerDisconnected()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * App-side backup of the mark-as-read pipeline: when the hook-side DB write leaves the
     * SMS app's notification standing (vendor apps that keep unread state outside the
     * telephony provider), click the app's own mark-as-read semantic action, or cancel the
     * notification when the app exposes none. No-ops unless the user enabled mark-as-read.
     */
    private suspend fun maybeMarkSmsNotificationRead(
        context: Context,
        sbn: StatusBarNotification,
    ) {
        if (!AppPreferencesDataStore.getBoolean(
                context,
                RelayPrefConst.KEY_MARK_AS_READ,
                false,
            )
        ) {
            return
        }
        val notification = sbn.notification ?: return
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val decision = CodeNotificationReadCoordinator(
            codeParser = { input ->
                RelaySmsCodeUtils.parseSmsCodeIfExists(
                    context,
                    input,
                    source = SmsCodeParseSource(
                        packageName = sbn.packageName,
                        sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
                    ),
                ).takeIf { it.isNotBlank() }
            },
            actionSender = { action -> action.actionIntent?.send() },
        ).maybeMarkAsRead(
            packageName = sbn.packageName,
            title = title,
            text = text,
            notification = notification,
        )
        if (decision == CodeNotificationReadCoordinator.Decision.NO_READ_ACTION) {
            runCatching { cancelNotification(sbn.key) }.onFailure { error ->
                XLog.w(
                    message = "NLS: notification cancel failed pkg=%s err=%s",
                    sbn.packageName,
                    error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        return runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xinyi-relay:notification-dispatch")
            wl.acquire(WAKE_LOCK_TIMEOUT_MS)
            wl
        }.onFailure { e ->
            XLog.w(message = "Failed to acquire wake lock: %s", e.message)
        }.getOrNull()
    }

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)

    private fun emitNotify(
        result: String,
        reason: String,
        stage: String,
        targetPackage: String = "",
        codePresent: Boolean? = null,
        eventIdPresent: Boolean? = null,
        durationMs: Long = 0L,
        statusOk: Boolean = true,
        errorClass: String? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "main",
            "stage" to stage,
            "reason" to reason,
            "source" to "notification_listener",
        )
        if (targetPackage.isNotBlank()) {
            attrs["target_package"] = targetPackage
        }
        if (codePresent != null) {
            attrs["code_present"] = codePresent.toString()
        }
        if (eventIdPresent != null) {
            attrs["event_id_present"] = eventIdPresent.toString()
        }
        if (!errorClass.isNullOrBlank()) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(name = "notify.owned", attributes = attrs, statusOk = statusOk)
    }

    private companion object {
        const val WAKE_LOCK_TIMEOUT_MS = 30_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
