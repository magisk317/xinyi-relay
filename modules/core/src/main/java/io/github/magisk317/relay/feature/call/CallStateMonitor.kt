package io.github.magisk317.relay.feature.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.CallSessionTracker
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.feature.reminder.SpecialAlertCoordinator
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.platform.ipc.CallIngressAdapter
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.magisk317.xposed.logging.MagiskOtel

object CallStateMonitor {
    private const val RINGING_DEDUP_MS = 8_000L
    private const val CALL_TYPE_INCOMING = 1
    private const val CALL_TYPE_OUTGOING = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var started = false
    @Volatile
    private var lastRingingAt: Long = 0L
    @Volatile
    private var lastState: Int = TelephonyManager.CALL_STATE_IDLE
    @Volatile
    private var lastNumber: String? = null
    @Volatile
    private var lastDirection: Int = 0
    @Volatile
    private var callStartedAt: Long = 0L
    private var legacyListener: Any? = null
    private var telephonyCallback: TelephonyCallback? = null
    @Volatile
    private var pendingEndedCallJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        refresh("init")
    }

    fun refresh(reason: String) {
        val context = appContext ?: return
        scope.launch {
            val mode = WorkModeResolver.mode.value
            if (mode != WorkMode.Standard) {
                stop("mode_$mode")
                return@launch
            }
            val (forwardEnabled, localEnabled) = loadCallAlertFlags(context)
            if (!forwardEnabled && !localEnabled) {
                stop("disabled")
                return@launch
            }
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                stop("no_permission")
                return@launch
            }
            if (started) return@launch
            start(context, reason)
        }
    }

    private fun start(context: Context, reason: String) {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager? ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(context, state, null)
                }
            }
            manager.registerTelephonyCallback(context.mainExecutor, callback)
            telephonyCallback = callback
            started = true
            XLog.i("CallStateMonitor started (callback) reason=%s", reason)
            MagiskOtel.event(
                name = "call.alert",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "start",
                    "reason" to reason,
                    "source" to "callback",
                ),
                statusOk = true,
            )
        } else {
            legacyListener = LegacyCallStateListener.register(manager) { state, phoneNumber ->
                handleCallState(context, state, phoneNumber)
            }
            started = true
            XLog.i("CallStateMonitor started (listener) reason=%s", reason)
            MagiskOtel.event(
                name = "call.alert",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "start",
                    "reason" to reason,
                    "source" to "listener",
                ),
                statusOk = true,
            )
        }
    }

    private fun stop(reason: String) {
        cancelPendingEndedCall("monitor_$reason")
        val context = appContext ?: return
        if (!started) return
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager? ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { manager.unregisterTelephonyCallback(it) }
            telephonyCallback = null
        } else {
            LegacyCallStateListener.unregister(manager, legacyListener)
            legacyListener = null
        }
        started = false
        XLog.i("CallStateMonitor stopped reason=%s", reason)
        MagiskOtel.event(
            name = "call.alert",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "stop",
                "reason" to reason,
            ),
            statusOk = true,
        )
    }

    private fun handleCallState(context: Context, state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                cancelPendingEndedCall("new_ringing_call")
                val now = System.currentTimeMillis()
                if (now - lastRingingAt < RINGING_DEDUP_MS) {
                    MagiskOtel.event(
                        name = "call.alert",
                        attributes = mapOf(
                            "result" to "skip",
                            "duration_ms" to "0",
                            "process" to "app",
                            "stage" to "state",
                            "reason" to "ringing_dedup",
                        ),
                        statusOk = true,
                    )
                    return
                }
                lastRingingAt = now
                lastNumber = phoneNumber?.ifBlank { null }
                lastDirection = CALL_TYPE_INCOMING
                callStartedAt = now
                val title = context.getString(R.string.call_alert_notification_title)
                val display = CallIngressAdapter.displayName(
                    phoneNumber = lastNumber,
                    fallbackTitle = title,
                )
                val payload = CallIngressAdapter.ringingPayload(
                    packageName = context.packageName,
                    fallbackTitle = title,
                    phoneNumber = lastNumber,
                    incomingBody = context.getString(
                        R.string.call_alert_notification_content,
                        display,
                    ),
                    company = title,
                    timestamp = now,
                    callType = CALL_TYPE_INCOMING,
                )
                scope.launch {
                    SpecialAlertCoordinator.notifyForEvent(
                        context = context,
                        event = payload.toRelayEvent(),
                    )
                    val (forwardEnabled, _) = loadCallAlertFlags(context)
                    if (forwardEnabled) {
                        sendCallBroadcast(
                            context = context,
                            stage = "ringing",
                            callType = CALL_TYPE_INCOMING,
                            number = lastNumber,
                        )
                    }
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                cancelPendingEndedCall("call_offhook")
                if (lastState == TelephonyManager.CALL_STATE_IDLE) {
                    lastDirection = CALL_TYPE_OUTGOING
                    callStartedAt = System.currentTimeMillis()
                } else if (callStartedAt == 0L) {
                    callStartedAt = System.currentTimeMillis()
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState != TelephonyManager.CALL_STATE_IDLE) {
                    val endedAt = System.currentTimeMillis()
                    val sessionStartedAt = if (callStartedAt > 0L) callStartedAt else endedAt
                    val expectedCallType = if (lastDirection == 0) CALL_TYPE_INCOMING else lastDirection
                    val directNumber = lastNumber?.ifBlank { null }
                    scheduleEndedCallDispatch(
                        context = context,
                        expectedCallType = expectedCallType,
                        sessionStartedAt = sessionStartedAt,
                        endedAt = endedAt,
                        directNumber = directNumber,
                    )
                }
                lastNumber = null
                lastDirection = 0
                callStartedAt = 0L
            }
        }
        lastState = state
    }

    private fun scheduleEndedCallDispatch(
        context: Context,
        expectedCallType: Int,
        sessionStartedAt: Long,
        endedAt: Long,
        directNumber: String?,
    ) {
        cancelPendingEndedCall("superseded_ended_call")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val (forwardEnabled, _) = loadCallAlertFlags(context)
            if (!forwardEnabled) return@launch

            val resolution = CallEndResolver.resolve(
                directNumber = directNumber,
                expectedCallType = expectedCallType,
                retryAllowed = CallLogQueryHelper.canReadCallLog(context),
                recentNumberProvider = {
                    CallSessionTracker.findRecentNumber(expectedCallType)
                },
                callLogProvider = {
                    CallLogQueryHelper.findRecentCall(
                        context = context,
                        expectedCallType = expectedCallType,
                        sessionStartedAt = sessionStartedAt,
                        endedAt = endedAt,
                    )
                },
            )
            when (resolution.source) {
                CallEndResolutionPolicy.NumberSource.Direct -> Unit
                CallEndResolutionPolicy.NumberSource.RecentIngress ->
                    XLog.i("CallStateMonitor recovered call number from recent ingress")
                CallEndResolutionPolicy.NumberSource.CallLog ->
                    XLog.i(
                        "CallStateMonitor recovered call number from CallLog after %d attempt(s)",
                        resolution.attemptCount,
                    )
                CallEndResolutionPolicy.NumberSource.Unavailable ->
                    XLog.i("CallStateMonitor ended call number unavailable, using fallback title")
            }
            sendCallBroadcast(
                context = context,
                stage = "ended",
                callType = resolution.callType,
                number = resolution.number,
            )
        }
        pendingEndedCallJob = job
        job.invokeOnCompletion {
            if (pendingEndedCallJob === job) pendingEndedCallJob = null
        }
        job.start()
    }

    private fun cancelPendingEndedCall(reason: String) {
        val job = pendingEndedCallJob ?: return
        pendingEndedCallJob = null
        if (!job.isCompleted) {
            job.cancel()
            XLog.i("CallStateMonitor cancelled pending ended-call lookup reason=%s", reason)
        }
    }

    private suspend fun sendCallBroadcast(
        context: Context,
        stage: String,
        callType: Int,
        number: String?,
    ) {
        val title = context.getString(R.string.call_alert_notification_title)
        val display = CallIngressAdapter.displayName(
            phoneNumber = number,
            fallbackTitle = title,
        )
        val body = if (stage == "ended") {
            context.getString(R.string.call_alert_notification_end_content, display)
        } else {
            context.getString(R.string.call_alert_notification_content, display)
        }
        val timestamp = System.currentTimeMillis()
        val payload = if (stage == "ringing") {
            CallIngressAdapter.ringingPayload(
                packageName = context.packageName,
                fallbackTitle = title,
                phoneNumber = number,
                incomingBody = body,
                company = title,
                timestamp = timestamp,
                callType = callType,
            )
        } else {
            CallIngressAdapter.stagePayload(
                packageName = context.packageName,
                fallbackTitle = title,
                phoneNumber = number,
                body = body,
                company = title,
                timestamp = timestamp,
                callType = callType,
                stage = stage,
            )
        }
        val startedAt = System.nanoTime()
        val outcome = runCatching {
            ForwardBroadcastDispatcher.dispatchFromHost(
                context = context,
                payload = payload,
            )
        }
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "call.alert",
            attributes = mapOf(
                "result" to if (outcome.isSuccess) "ok" else "error",
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "dispatch",
                "reason" to stage,
                "source" to when (callType) {
                    CALL_TYPE_INCOMING -> "incoming"
                    CALL_TYPE_OUTGOING -> "outgoing"
                    else -> "unknown"
                },
            ) + if (outcome.isFailure) {
                mapOf("error_class" to (outcome.exceptionOrNull()?.javaClass?.simpleName ?: "Throwable"))
            } else {
                emptyMap()
            },
            statusOk = outcome.isSuccess,
        )
    }

    private suspend fun loadCallAlertFlags(context: Context): Pair<Boolean, Boolean> {
        val runtimeGraph = RuntimeGraph.from(context)
        val messageTypeEnabled = RuntimeSettingsCache.getBoolean(
            key = PrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED,
            defaultValue = false,
        ) { key, defaultValue ->
            runtimeGraph.preferenceDataSource.getBoolean(key, defaultValue)
        }
        val finalForwardEnabled = RuntimeSettingsCache.getBoolean(
            key = PrefConst.KEY_FORWARD_CALL_NOTIFY_FINAL_ENABLED,
            defaultValue = false,
        ) { key, defaultValue ->
            runtimeGraph.preferenceDataSource.getBoolean(key, defaultValue)
        }
        val forwardEnabled = messageTypeEnabled || RuntimeSettingsCache.getBoolean(
            key = PrefConst.KEY_CALL_ALERT_FORWARD_ENABLED,
            defaultValue = false,
        ) { key, defaultValue ->
            runtimeGraph.preferenceDataSource.getBoolean(key, defaultValue)
        } || finalForwardEnabled
        val localEnabled = RuntimeSettingsCache
            .getSpecialAlertSettings(runtimeGraph.settingsRepository)
            .callAlertLocalEnabled
        return forwardEnabled to localEnabled
    }
}
