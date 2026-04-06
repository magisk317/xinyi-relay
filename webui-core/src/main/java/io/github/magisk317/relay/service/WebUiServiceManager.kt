package io.github.magisk317.relay.service

import android.content.Context
import io.github.magisk317.relay.common.feature.WebUiFeatureGate
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.webui.WebUiConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WebUiServiceManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var configJob: Job? = null
    private val configStore by lazy { WebUiConfigStore(context) }

    fun start() {
        if (configJob != null) return
        if (!WebUiFeatureGate.EMBEDDED_WEBUI_ENABLED) {
            XLog.w("WebUI service manager disabled by feature gate, stopping foreground service")
            WebUiForegroundService.stop(context)
            return
        }
        XLog.i("WebUI service manager start requested")
        configJob = scope.launch {
            configStore.ensureInitialized()
            configStore.observe().collect { snapshot ->
                XLog.i(
                    "WebUI config observed enabled=%s host=%s port=%d lan=%s username=%s",
                    snapshot.enabled,
                    snapshot.host,
                    snapshot.port,
                    snapshot.allowLanAccess,
                    snapshot.username,
                )
                if (!snapshot.enabled) {
                    XLog.w("WebUI config disabled, stopping foreground service")
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
        XLog.i("WebUI service manager stop requested")
        configJob?.cancel()
        configJob = null
        scope.cancel()
        WebUiForegroundService.stop(context)
    }
}
