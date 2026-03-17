package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.domain.pipeline.StorageRuntimeGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

class SecurityInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        scope.launch {
            val preferenceDataSource = StorageRuntimeGraph.from(application).preferenceDataSource
            val token = preferenceDataSource.getString(PrefConst.KEY_IPC_TOKEN, "")
            if (token.isEmpty()) {
                val newToken = UUID.randomUUID().toString()
                preferenceDataSource.setString(PrefConst.KEY_IPC_TOKEN, newToken)
                Timber.i("Generated new IPC Security Token via DataStore")
            }
            preferenceDataSource.ensureReadable()
        }
    }
}
