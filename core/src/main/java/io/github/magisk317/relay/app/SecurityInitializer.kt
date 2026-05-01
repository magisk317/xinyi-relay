package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

class SecurityInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        AppInitExecution.runWhenUserUnlocked(application, scope, "SecurityInitializer") {
            val preferenceDataSource = RuntimeGraph.from(application).preferenceDataSource
            val token = preferenceDataSource.getString(PrefConst.KEY_IPC_TOKEN, "")
            if (token.isEmpty()) {
                val newToken = UUID.randomUUID().toString()
                preferenceDataSource.setString(PrefConst.KEY_IPC_TOKEN, newToken)
                Timber.i("Generated new IPC Security Token via DataStore")
            }
            HookPreferenceMirror.publish(application)
        }
    }
}
