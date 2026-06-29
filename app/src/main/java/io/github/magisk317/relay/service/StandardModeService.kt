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
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.feature.mode.BatteryOptimizationHelper
import io.github.magisk317.relay.feature.mode.StandardModePermissions
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app alive in Standard mode (non-Xposed).
 *
 * Responsibilities:
 * - Shows a persistent notification so the system is less likely to kill the process
 * - Monitors WorkMode changes and stops itself if mode is no longer Standard
 * - Ensures CallStateMonitor stays initialized
 *
 * This service is NOT needed in Enhanced mode (Xposed), because the Xposed hook
 * keeps the module process alive via the system_server injection.
 */
class StandardModeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        XLog.i("StandardModeService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                monitorWorkMode()
                XLog.i("StandardModeService started as foreground service")
            }
            ACTION_STOP -> {
                XLog.i("StandardModeService stopping on user/system request")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        XLog.i("StandardModeService destroyed")
        super.onDestroy()
    }

    private fun monitorWorkMode() {
        scope.launch {
            WorkModeResolver.mode.collectLatest { mode ->
                if (mode != WorkMode.Standard) {
                    XLog.i("StandardModeService: mode changed to %s, stopping service", mode)
                    stopSelf()
                    return@collectLatest
                }

                // Re-check and refresh CallStateMonitor if needed
                if (StandardModePermissions.allGranted(this@StandardModeService)) {
                    CallStateMonitor.refresh("standard_mode_service")
                }

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
                "Standard Mode",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps xinyi-relay running in standard mode (non-Xposed)"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // TODO: Launch MainActivity when clicked
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("xinyi-relay running")
            .setContentText("Standard mode active - SMS and call monitoring")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "StandardModeService"
        private const val CHANNEL_ID = "standard_mode_service"
        private const val NOTIFICATION_ID = 0x584D // "XM" in hex
        private const val ACTION_START = "io.github.magisk317.relay.ACTION_START_STANDARD_MODE"
        private const val ACTION_STOP = "io.github.magisk317.relay.ACTION_STOP_STANDARD_MODE"

        fun start(context: Context) {
            val intent = Intent(context, StandardModeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StandardModeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
