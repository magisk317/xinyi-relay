package io.github.magisk317.relay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.feature.mode.BatteryOptimizationHelper
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.ui.home.LauncherActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps cross-device message relay active in Standard mode (non-Xposed).
 *
 * Responsibilities:
 * - Shows a persistent notification for the ongoing remote-messaging relay
 * - Monitors WorkMode changes and stops itself if mode is no longer Standard
 * - Ensures CallStateMonitor stays initialized
 *
 * This service is NOT needed in Enhanced mode (Xposed), because the Xposed hook
 * keeps the module process alive via the system_server injection.
 */
class StandardModeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Must promote to FGS before any later stopService() race (Xposed bind can flip
        // Standard -> Enhanced within tens of ms after app_init reconcile).
        startForeground(NOTIFICATION_ID, buildNotification())
        XLog.i("StandardModeService created and promoted to foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                // Refresh notification content; onCreate already called startForeground.
                startForeground(NOTIFICATION_ID, buildNotification())
                monitorWorkMode()
                XLog.i("StandardModeService started as foreground service")
                START_STICKY
            }
            ACTION_STOP -> {
                XLog.i("StandardModeService stopping on user/system request")
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                XLog.w("StandardModeService received unknown action: %s", intent?.action)
                stopSelf()
                START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        XLog.i("StandardModeService destroyed")
        super.onDestroy()
    }

    private fun monitorWorkMode() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            WorkModeResolver.mode.collectLatest { mode ->
                if (mode != WorkMode.Standard) {
                    CallStateMonitor.refresh("standard_service_mode_$mode")
                    XLog.i("StandardModeService: mode changed to %s, stopping service", mode)
                    stopSelf()
                    return@collectLatest
                }

                // Re-check and refresh CallStateMonitor if needed
                CallStateMonitor.refresh("standard_mode_service")

                // Log battery optimization status
                if (!BatteryOptimizationHelper.isExempted(this@StandardModeService)) {
                    XLog.w("StandardModeService: not exempted from battery optimization")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.standard_mode_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.standard_mode_service_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, LauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            pendingIntentFlags,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            // Brand monochrome (ic_launcher_monochrome via ic_notification). Do not use
            // android.R.drawable.* — framework ids tagged with this package become blank on HyperOS.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.standard_mode_service_notification_title))
            .setContentText(getString(R.string.standard_mode_service_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "standard_mode_service"
        private const val NOTIFICATION_ID = 0x584D // "XM" in hex
        private const val ACTION_START = "io.github.magisk317.relay.ACTION_START_STANDARD_MODE"
        private const val ACTION_STOP = "io.github.magisk317.relay.ACTION_STOP_STANDARD_MODE"

        fun start(context: Context) {
            if (!BuildConfig.ENABLE_STANDARD_MODE_SERVICE) {
                XLog.i("StandardModeService disabled for this distribution")
                return
            }
            val intent = Intent(context, StandardModeService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { error ->
                XLog.e("Failed to start StandardModeService", error)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, StandardModeService::class.java))
            }.onFailure { error ->
                XLog.e("Failed to stop StandardModeService", error)
            }
        }

        fun reconcile(context: Context, mode: WorkMode, reason: String) {
            if (!BuildConfig.ENABLE_STANDARD_MODE_SERVICE) {
                XLog.i(
                    "StandardModeService reconcile skipped: distribution disabled mode=%s reason=%s",
                    mode,
                    reason,
                )
                return
            }
            CallStateMonitor.refresh("work_mode_$reason")
            when (mode) {
                WorkMode.Standard -> start(context)
                WorkMode.Enhanced,
                WorkMode.Inactive,
                -> stop(context)
            }
        }
    }
}
