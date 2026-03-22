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
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.constant.NotificationConst
import io.github.magisk317.relay.common.utils.NotificationUtils
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.domain.pipeline.StorageRuntimeGraph
import io.github.magisk317.relay.ui.home.MainActivity
import io.github.magisk317.relay.web.WebUiRuntimeConfig
import io.github.magisk317.relay.web.WebUiServer
import io.github.magisk317.relay.web.WebUiTlsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class WebUiForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferenceDataSource by lazy { StorageRuntimeGraph.from(this).preferenceDataSource }
    private var applyJob: Job? = null
    private var webUiServer: WebUiServer? = null
    private var runningConfig: RunningConfig? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: runningConfig?.port ?: DEFAULT_PORT
                val allowLanAccess = intent?.getBooleanExtra(EXTRA_ALLOW_LAN_ACCESS, false)
                    ?: runningConfig?.allowLanAccess
                    ?: false
                ensureChannel()
                ServiceCompat.startForeground(
                    this,
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
        return START_STICKY
    }

    override fun onDestroy() {
        applyJob?.cancel()
        stopServer()
        serviceScope.cancel()
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

    private suspend fun applyCurrentConfig() {
        val snapshot = loadConfigSnapshot()
        if (!snapshot.enabled) {
            stopServer()
            stopSelf()
            return
        }

        if (snapshot == runningConfig && webUiServer != null) {
            updateForegroundNotification(snapshot)
            return
        }

        runCatching {
            val tlsMaterial = WebUiTlsManager.loadOrCreate(
                context = this,
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
            WebUiServer(this, runtimeConfig).also {
                it.start()
                webUiServer = it
            }
            runningConfig = snapshot
            updateForegroundNotification(snapshot)
        }.onFailure {
            stopServer()
            Timber.e(
                it,
                "Failed to start WebUI server (host=%s port=%s lan=%s)",
                snapshot.host,
                snapshot.port,
                snapshot.allowLanAccess,
            )
            stopSelf()
        }
    }

    private fun updateForegroundNotification(snapshot: RunningConfig) {
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(snapshot.port, snapshot.allowLanAccess),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private suspend fun loadConfigSnapshot(): RunningConfig {
        val enabled = preferenceDataSource.getBoolean(PrefConst.KEY_WEBUI_ENABLE, true)
        val allowLanAccess = preferenceDataSource.getBoolean(PrefConst.KEY_WEBUI_LAN_ACCESS, false)
        val port = preferenceDataSource.getString(PrefConst.KEY_WEBUI_PORT, PrefConst.KEY_WEBUI_PORT_DEFAULT)
            .toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: DEFAULT_PORT
        val username = preferenceDataSource.getString(
            PrefConst.KEY_WEBUI_USERNAME,
            PrefConst.KEY_WEBUI_USERNAME_DEFAULT,
        ).ifBlank { PrefConst.KEY_WEBUI_USERNAME_DEFAULT }
        val password = preferenceDataSource.getString(PrefConst.KEY_WEBUI_PASSWORD, "")
        return RunningConfig(
            enabled = enabled,
            host = if (allowLanAccess) "0.0.0.0" else "127.0.0.1",
            port = port,
            username = username,
            password = password,
            allowLanAccess = allowLanAccess,
        )
    }

    private fun stopServer() {
        webUiServer?.stop()
        webUiServer = null
        runningConfig = null
    }

    private data class RunningConfig(
        val enabled: Boolean,
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val allowLanAccess: Boolean,
    )

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
