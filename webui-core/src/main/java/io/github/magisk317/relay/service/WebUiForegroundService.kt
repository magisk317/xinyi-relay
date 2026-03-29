package io.github.magisk317.relay.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat

class WebUiForegroundService : Service() {
    private val controller by lazy { WebUiForegroundController(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = controller.onStartCommand(intent)

    override fun onDestroy() {
        controller.onDestroy()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_NAMESPACE = "io.github.magisk317.relay.action"
        private const val ACTION_START = "$ACTION_NAMESPACE.WEBUI_FOREGROUND_START"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_ALLOW_LAN_ACCESS = "allow_lan_access"

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
