package io.github.magisk317.relay.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.magisk317.relay.common.constant.NotificationConst
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.common.utils.NotificationUtils
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.webui.WebUiConfigSnapshot
import io.github.magisk317.relay.webui.WebUiConfigStore
import io.github.magisk317.relay.webui.WebUiRuntimeConfig
import io.github.magisk317.relay.webui.WebUiServer
import io.github.magisk317.relay.webui.WebUiTlsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class WebUiForegroundController(private val service: Service) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configStore by lazy { WebUiConfigStore(service) }
    private var applyJob: Job? = null
    private var webUiServer: WebUiServer? = null
    private var runningConfig: WebUiConfigSnapshot? = null

    fun onStartCommand(intent: Intent?): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: runningConfig?.port ?: DEFAULT_PORT
                val allowLanAccess = intent?.getBooleanExtra(EXTRA_ALLOW_LAN_ACCESS, false)
                    ?: runningConfig?.allowLanAccess
                    ?: false
                XLog.i(
                    "WebUI foreground controller start action=%s port=%d lan=%s",
                    intent?.action ?: ACTION_START,
                    port,
                    allowLanAccess,
                )
                ensureChannel()
                ServiceCompat.startForeground(
                    service,
                    NOTIFICATION_ID,
                    buildNotification(port, allowLanAccess),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                applyJob?.cancel()
                applyJob = serviceScope.launch {
                    applyCurrentConfig()
                }
            }
        }
        return Service.START_STICKY
    }

    fun onDestroy() {
        XLog.w("WebUI foreground controller destroyed")
        applyJob?.cancel()
        stopServer()
        serviceScope.cancel()
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    private fun ensureChannel() {
        NotificationUtils.createNotificationChannel(
            context = service,
            channelId = NotificationConst.CHANNEL_ID_FOREGROUND_SERVICE,
            channelName = service.getString(R.string.webui_foreground_channel_name),
            importance = NotificationManager.IMPORTANCE_LOW,
        )
    }

    private fun buildNotification(port: Int, allowLanAccess: Boolean) =
        NotificationCompat.Builder(service, NotificationConst.CHANNEL_ID_FOREGROUND_SERVICE)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle(service.getString(R.string.webui_foreground_title))
            .setContentText(
                service.getString(
                    if (allowLanAccess) {
                        R.string.webui_foreground_content_lan
                    } else {
                        R.string.webui_foreground_content_local
                    },
                    port,
                ),
            )
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

    private suspend fun applyCurrentConfig() {
        val snapshot = configStore.loadSnapshot()
        XLog.i(
            "WebUI apply config enabled=%s host=%s port=%d lan=%s username=%s",
            snapshot.enabled,
            snapshot.host,
            snapshot.port,
            snapshot.allowLanAccess,
            snapshot.username,
        )
        if (!snapshot.enabled) {
            XLog.w("WebUI apply config aborted because feature is disabled")
            stopServer()
            service.stopSelf()
            return
        }

        if (snapshot == runningConfig && webUiServer != null) {
            XLog.i("WebUI config unchanged, refreshing foreground notification only")
            updateForegroundNotification(snapshot)
            return
        }

        runCatching {
            val tlsMaterial = WebUiTlsManager.loadOrCreate(
                context = service,
                includeLocalNetworkHosts = snapshot.allowLanAccess,
            )
            val runtimeConfig = WebUiRuntimeConfig(
                host = snapshot.host,
                port = snapshot.port,
                username = snapshot.username,
                password = snapshot.password,
                allowLanAccess = snapshot.allowLanAccess,
                tlsMaterial = tlsMaterial,
            )
            stopServer()
            WebUiServer(service, runtimeConfig).also {
                it.start()
                webUiServer = it
            }
            runningConfig = snapshot
            XLog.i(
                "WebUI server applied host=%s port=%d lan=%s",
                snapshot.host,
                snapshot.port,
                snapshot.allowLanAccess,
            )
            updateForegroundNotification(snapshot)
        }.onFailure {
            stopServer()
            XLog.e(
                "WebUI start failed host=${snapshot.host} port=${snapshot.port} lan=${snapshot.allowLanAccess}",
                it,
            )
            Timber.e(
                it,
                "Failed to start WebUI server (host=%s port=%s lan=%s)",
                snapshot.host,
                snapshot.port,
                snapshot.allowLanAccess,
            )
            service.stopSelf()
        }
    }

    private fun updateForegroundNotification(snapshot: WebUiConfigSnapshot) {
        ensureChannel()
        ServiceCompat.startForeground(
            service,
            NOTIFICATION_ID,
            buildNotification(snapshot.port, snapshot.allowLanAccess),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun stopServer() {
        if (webUiServer != null) {
            XLog.i("WebUI server stop requested")
        }
        webUiServer?.stop()
        webUiServer = null
        runningConfig = null
    }

    companion object {
        private const val ACTION_START = "io.github.magisk317.relay.action.WEBUI_FOREGROUND_START"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_ALLOW_LAN_ACCESS = "allow_lan_access"
        private const val NOTIFICATION_ID = 0x52454c
        private const val DEFAULT_PORT = 8787
    }
}
