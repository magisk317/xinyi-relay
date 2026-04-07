package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RemoteAgentInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        AppInitExecution.runWhenUserUnlocked(application, scope, "RemoteAgentInitializer") {
            RuntimeGraph.from(application).remoteAgentRepository.startupSync()
        }
    }
}
