package io.github.magisk317.relay.web

import android.content.Context
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.domain.pipeline.StorageRuntimeGraph
import io.github.magisk317.relay.service.WebUiForegroundService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

class WebUiManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webUiServer: WebUiServer? = null
    private var configJob: Job? = null
    private val preferenceDataSource by lazy { StorageRuntimeGraph.from(context).preferenceDataSource }

    fun start() {
        if (configJob != null) return
        configJob = scope.launch {
            ensureWebUiConfigInitialized()
            combine(
                preferenceDataSource.getBooleanFlow(PrefConst.KEY_WEBUI_ENABLE, true),
                preferenceDataSource.getBooleanFlow(PrefConst.KEY_WEBUI_LAN_ACCESS, false),
                preferenceDataSource.getStringFlow(PrefConst.KEY_WEBUI_PORT, PrefConst.KEY_WEBUI_PORT_DEFAULT),
                preferenceDataSource.getStringFlow(PrefConst.KEY_WEBUI_USERNAME, PrefConst.KEY_WEBUI_USERNAME_DEFAULT),
                preferenceDataSource.getStringFlow(PrefConst.KEY_WEBUI_PASSWORD, ""),
            ) { webUiEnabled, allowLanAccess, portString, username, password ->
                val port = portString.toIntOrNull()
                    ?.takeIf { it in 1..65535 }
                    ?: PrefConst.KEY_WEBUI_PORT_DEFAULT.toInt()
                WebUiConfigSnapshot(
                    enabled = webUiEnabled,
                    host = if (allowLanAccess) "0.0.0.0" else "127.0.0.1",
                    port = port,
                    username = username.ifBlank { PrefConst.KEY_WEBUI_USERNAME_DEFAULT },
                    password = password,
                    allowLanAccess = allowLanAccess,
                )
            }.distinctUntilChanged().collect { snapshot ->
                if (!snapshot.enabled) {
                    stopServer()
                    return@collect
                }
                startServer(snapshot)
            }
        }
    }

    private suspend fun startServer(snapshot: WebUiConfigSnapshot) {
        runCatching {
            val tlsMaterial = WebUiTlsManager.loadOrCreate(context)
            val runtimeConfig = WebUiRuntimeConfig(
                host = snapshot.host,
                port = snapshot.port,
                username = snapshot.username,
                password = snapshot.password,
                allowLanAccess = snapshot.allowLanAccess,
                tlsMaterial = tlsMaterial,
            )
            stopServer()
            WebUiServer(context, runtimeConfig).also {
                it.start()
                webUiServer = it
            }
            WebUiForegroundService.start(context, snapshot.port, snapshot.allowLanAccess)
        }.onFailure {
            WebUiForegroundService.stop(context)
            Timber.e(it, "Failed to start WebUI server (host=%s port=%s lan=%s)", snapshot.host, snapshot.port, snapshot.allowLanAccess)
        }
    }

    private fun stopServer() {
        webUiServer?.stop()
        webUiServer = null
        WebUiForegroundService.stop(context)
    }

    fun stop() {
        configJob?.cancel()
        configJob = null
        stopServer()
    }

    private suspend fun ensureWebUiConfigInitialized() {
        val webUiEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_WEBUI_ENABLE, true)
        preferenceDataSource.setBoolean(PrefConst.KEY_WEBUI_ENABLE, webUiEnabled)
        val port = preferenceDataSource.getString(PrefConst.KEY_WEBUI_PORT, "")
        if (port.isBlank()) {
            preferenceDataSource.setString(PrefConst.KEY_WEBUI_PORT, PrefConst.KEY_WEBUI_PORT_DEFAULT)
        }
        val username = preferenceDataSource.getString(PrefConst.KEY_WEBUI_USERNAME, "")
        if (username.isBlank() || username == "xsmscode") {
            preferenceDataSource.setString(PrefConst.KEY_WEBUI_USERNAME, PrefConst.KEY_WEBUI_USERNAME_DEFAULT)
        }
        val password = preferenceDataSource.getString(PrefConst.KEY_WEBUI_PASSWORD, "")
        if (password.isBlank()) {
            preferenceDataSource.setString(
                PrefConst.KEY_WEBUI_PASSWORD,
                WebUiTlsManager.generateRandomCredential(8),
            )
        }
    }

    private data class WebUiConfigSnapshot(
        val enabled: Boolean,
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val allowLanAccess: Boolean,
    )
}
