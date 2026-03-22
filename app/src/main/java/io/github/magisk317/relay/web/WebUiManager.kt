package io.github.magisk317.relay.web

import android.content.Context
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.domain.pipeline.StorageRuntimeGraph
import io.github.magisk317.relay.service.WebUiForegroundService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class WebUiManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
                    WebUiForegroundService.stop(context)
                    return@collect
                }
                WebUiForegroundService.start(
                    context = context,
                    port = snapshot.port,
                    allowLanAccess = snapshot.allowLanAccess,
                )
            }
        }
    }

    fun stop() {
        configJob?.cancel()
        configJob = null
        WebUiForegroundService.stop(context)
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
