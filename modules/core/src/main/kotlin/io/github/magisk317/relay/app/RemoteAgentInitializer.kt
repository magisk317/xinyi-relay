package io.github.magisk317.relay.app

import android.app.Application
import android.database.ContentObserver
import io.github.magisk317.relay.android.data.db.DBProvider
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.repository.ConfigSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RemoteAgentInitializer(
    private val coordinatorProvider: (Application) -> ConfigSyncCoordinator = { application ->
        RuntimeGraph.from(application).configSyncCoordinator
    },
    private val recordObserverRegistrar: (Application, () -> Unit) -> Unit = ::registerRecordObserver,
    private val initRunner: (
        Application,
        CoroutineScope,
        String,
        suspend () -> Unit,
    ) -> Unit = AppInitExecution::runWhenUserUnlocked,
) : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        initRunner(application, scope, "RemoteAgentInitializer") {
            val coordinator = coordinatorProvider(application)
            recordObserverRegistrar(application) {
                coordinator.scheduleRecordUpload(PROVIDER_CHANGE_REASON)
            }
            coordinator.startupSync()
        }
    }

    private companion object {
        const val PROVIDER_CHANGE_REASON = "provider_change"

        fun registerRecordObserver(application: Application, onRecordChanged: () -> Unit) {
            application.contentResolver.registerContentObserver(
                DBProvider.smsMsgContentUri(application),
                true,
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        onRecordChanged()
                    }
                },
            )
        }
    }
}
