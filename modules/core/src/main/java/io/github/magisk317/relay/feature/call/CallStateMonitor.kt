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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        } else {
            legacyListener = LegacyCallStateListener.register(manager) { state, phoneNumber ->
                handleCallState(context, state, phoneNumber)
            }
            started = true
            XLog.i("CallStateMonitor started (listener) reason=%s", reason)
        }
    }

    private fun stop(reason: String) {
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
    }

    private fun handleCallState(context: Context, state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                val now = System.currentTimeMillis()
                if (now - lastRingingAt < RINGING_DEDUP_MS) return
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
                    scope.launch {
                        val (forwardEnabled, _) = loadCallAlertFlags(context)
                        if (forwardEnabled) {
                            val callLogRow = CallLogQueryHelper.findRecentCall(
                                context = context,
                                expectedCallType = expectedCallType,
                                sessionStartedAt = sessionStartedAt,
                                endedAt = endedAt,
                            )
                            val recentNumber = CallSessionTracker.findRecentNumber(expectedCallType)
                            val callLogNumber = callLogRow?.number
                            val number = directNumber ?: recentNumber ?: callLogNumber
                            when {
                                directNumber == null && recentNumber != null ->
                                    XLog.i("CallStateMonitor recovered call number from recent ingress")
                                directNumber == null && recentNumber == null && callLogNumber != null ->
                                    XLog.i("CallStateMonitor recovered call number from CallLog")
                            }
                            if (number.isNullOrBlank()) {
                                XLog.i("CallStateMonitor ended call number unavailable, using fallback title")
                            }
                            sendCallBroadcast(
                                context = context,
                                stage = "ended",
                                callType = callLogRow?.callType ?: expectedCallType,
                                number = number,
                            )
                        }
                    }
                }
                lastNumber = null
                lastDirection = 0
                callStartedAt = 0L
            }
        }
        lastState = state
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
        runCatching {
            ForwardBroadcastDispatcher.dispatchFromHost(
                context = context,
                payload = payload,
            )
        }
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
