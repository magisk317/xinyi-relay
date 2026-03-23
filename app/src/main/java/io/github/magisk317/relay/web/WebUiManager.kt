package io.github.magisk317.relay.web

import android.content.Context
import io.github.magisk317.relay.service.WebUiForegroundService
import io.github.magisk317.relay.webui.WebUiConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WebUiManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var configJob: Job? = null
    private val configStore by lazy { WebUiConfigStore(context) }

    fun start() {
        if (configJob != null) return
        configJob = scope.launch {
            configStore.ensureInitialized()
            configStore.observe().collect { snapshot ->
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
}
