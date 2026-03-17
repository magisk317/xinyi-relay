package io.github.magisk317.relay.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.common.constant.NotificationConst
import io.github.magisk317.relay.common.utils.NotificationUtils
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.home.MainActivity

class WebUiForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
                val allowLanAccess = intent.getBooleanExtra(EXTRA_ALLOW_LAN_ACCESS, false)
                ensureChannel()
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(port, allowLanAccess),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun ensureChannel() {
        NotificationUtils.createNotificationChannel(
            context = this,
            channelId = NotificationConst.CHANNEL_ID_FOREGROUND_SERVICE,
            channelName = getString(R.string.webui_foreground_channel_name),
            importance = NotificationManager.IMPORTANCE_LOW,
        )
    }

    private fun buildNotification(port: Int, allowLanAccess: Boolean) =
        NotificationCompat.Builder(this, NotificationConst.CHANNEL_ID_FOREGROUND_SERVICE)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle(getString(R.string.webui_foreground_title))
            .setContentText(
                getString(
                    if (allowLanAccess) {
                        R.string.webui_foreground_content_lan
                    } else {
                        R.string.webui_foreground_content_local
                    },
                    port,
                ),
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

    companion object {
        private const val ACTION_NAMESPACE = "io.github.magisk317.relay.action"
        private const val ACTION_START = "$ACTION_NAMESPACE.WEBUI_FOREGROUND_START"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_ALLOW_LAN_ACCESS = "allow_lan_access"
        private const val NOTIFICATION_ID = 0x52454c
        private const val DEFAULT_PORT = 8787

        fun start(context: Context, port: Int, allowLanAccess: Boolean) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WebUiForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_PORT, port)
                    putExtra(EXTRA_ALLOW_LAN_ACCESS, allowLanAccess)
                },
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebUiForegroundService::class.java))
        }
    }
}
