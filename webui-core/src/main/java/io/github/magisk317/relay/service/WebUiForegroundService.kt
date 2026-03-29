package io.github.magisk317.relay.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.common.utils.XLog

class WebUiForegroundService : Service() {
    private val controller by lazy { WebUiForegroundController(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        XLog.i(
            "WebUI foreground service onStartCommand action=%s startId=%d port=%d lan=%s",
            intent?.action ?: ACTION_START,
            startId,
            intent?.getIntExtra(EXTRA_PORT, -1) ?: -1,
            intent?.getBooleanExtra(EXTRA_ALLOW_LAN_ACCESS, false) ?: false,
        )
        return controller.onStartCommand(intent)
    }

    override fun onDestroy() {
        XLog.w("WebUI foreground service destroyed")
        controller.onDestroy()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_NAMESPACE = "io.github.magisk317.relay.action"
        private const val ACTION_START = "$ACTION_NAMESPACE.WEBUI_FOREGROUND_START"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_ALLOW_LAN_ACCESS = "allow_lan_access"

        fun start(context: Context, port: Int, allowLanAccess: Boolean) {
            XLog.i(
                "WebUI foreground service start requested port=%d lan=%s",
                port,
                allowLanAccess,
            )
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
            XLog.i("WebUI foreground service stop requested")
            context.stopService(Intent(context, WebUiForegroundService::class.java))
        }
    }
}
