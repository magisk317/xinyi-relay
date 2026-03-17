package io.github.magisk317.relay.feature.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.feature.reminder.SpecialAlertCoordinator
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.domain.pipeline.StorageRuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.platform.ipc.ForwardReceiver
import java.util.UUID
import kotlinx.coroutines.runBlocking

object CallStateMonitor {
    private const val RINGING_DEDUP_MS = 8_000L
    private const val CALL_TYPE_INCOMING = 1
    private const val CALL_TYPE_OUTGOING = 2

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
    private var legacyListener: Any? = null
    private var telephonyCallback: TelephonyCallback? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        refresh("init")
    }

    fun refresh(reason: String) {
        val context = appContext ?: return
        if (BuildConfig.FLAVOR != "github") return
        val (forwardEnabled, localEnabled) = loadCallAlertFlags(context)
        if (!forwardEnabled && !localEnabled) {
            stop("disabled")
            return
        }
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            stop("no_permission")
            return
        }
        if (started) return
        start(context, reason)
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
                SpecialAlertCoordinator.notifyForEvent(
                    context = context,
                    event = RelayEvent(
                        messageType = MessageType.CALL_NOTIFY,
                        sourceType = "telephony_state",
                        sender = lastNumber ?: context.getString(R.string.call_alert_notification_title),
                        body = context.getString(
                            R.string.call_alert_notification_content,
                            lastNumber ?: context.getString(R.string.call_alert_notification_title),
                        ),
                        timestamp = now,
                        packageName = context.packageName,
                        notifyChannelId = "",
                        companyOrAppName = context.getString(R.string.call_alert_notification_title),
                        smsCode = null,
                        callType = CALL_TYPE_INCOMING,
                        callStage = "ringing",
                        simSlot = -1,
                        subId = 0,
                    ),
                )
                val (forwardEnabled, _) = loadCallAlertFlags(context)
                if (!forwardEnabled) {
                    lastState = state
                    return
                }
                sendCallBroadcast(
                    context = context,
                    stage = "ringing",
                    callType = CALL_TYPE_INCOMING,
                    number = lastNumber,
                )
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_IDLE) {
                    lastDirection = CALL_TYPE_OUTGOING
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState != TelephonyManager.CALL_STATE_IDLE) {
                    val (forwardEnabled, _) = loadCallAlertFlags(context)
                    if (forwardEnabled) {
                        val number = lastNumber?.ifBlank { null }
                        if (!number.isNullOrBlank()) {
                            sendCallBroadcast(
                                context = context,
                                stage = "ended",
                                callType = if (lastDirection == 0) CALL_TYPE_INCOMING else lastDirection,
                                number = number,
                            )
                        }
                    }
                }
                lastNumber = null
                lastDirection = 0
            }
        }
        lastState = state
    }

    private fun sendCallBroadcast(
        context: Context,
        stage: String,
        callType: Int,
        number: String?,
    ) {
        val display = number?.ifBlank { null } ?: context.getString(R.string.call_alert_notification_title)
        val body = if (stage == "ended") {
            context.getString(R.string.call_alert_notification_end_content, display)
        } else {
            context.getString(R.string.call_alert_notification_content, display)
        }
        val token = runBlocking {
            val runtimeGraph = StorageRuntimeGraph.from(context)
            RuntimeSettingsCache.getString(
                key = PrefConst.KEY_IPC_TOKEN,
                defaultValue = "",
            ) { key, defaultValue ->
                runtimeGraph.preferenceDataSource.getString(key, defaultValue)
            }
        }
        val intent = Intent(PrefConst.ACTION_FORWARD_SMS).apply {
            setClassName(context, ForwardReceiver::class.java.name)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra("sender", display)
            putExtra("body", body)
            putExtra("date", System.currentTimeMillis())
            putExtra("packageName", context.packageName)
            putExtra("msgType", "call_notify")
            putExtra("call_type", callType)
            putExtra("company", context.getString(R.string.call_alert_notification_title))
            putExtra("call_stage", stage)
            putExtra("forward_source", "telephony_state")
            putExtra("event_id", "tel_${UUID.randomUUID().toString().take(8)}")
            if (token.isNotBlank()) {
                putExtra("ipc_token", token)
            }
        }
        runCatching { context.sendBroadcast(intent) }
    }

    private fun loadCallAlertFlags(context: Context): Pair<Boolean, Boolean> = runBlocking {
        val runtimeGraph = StorageRuntimeGraph.from(context)
        val messageTypeEnabled = RuntimeSettingsCache.getBoolean(
            key = PrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED,
            defaultValue = false,
        ) { key, defaultValue ->
            runtimeGraph.preferenceDataSource.getBoolean(key, defaultValue)
        }
        val forwardEnabled = messageTypeEnabled || RuntimeSettingsCache.getBoolean(
            key = PrefConst.KEY_CALL_ALERT_FORWARD_ENABLED,
            defaultValue = false,
        ) { key, defaultValue ->
            runtimeGraph.preferenceDataSource.getBoolean(key, defaultValue)
        }
        val localEnabled = RuntimeSettingsCache
            .getSpecialAlertSettings(runtimeGraph.settingsRepository)
            .callAlertLocalEnabled
        forwardEnabled to localEnabled
    }
}
