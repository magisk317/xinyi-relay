package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.common.utils.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DataStoreSyncInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        AppInitExecution.runWhenUserUnlocked(application, scope, "DataStoreSyncInitializer") {
            val preferenceDataSource = RuntimeGraph.from(application).preferenceDataSource
            preferenceDataSource.syncToSharedPrefs()
            preferenceDataSource.ensureReadable()

            val verboseLog = preferenceDataSource.getBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, false)
            val logFileSizeMb = preferenceDataSource.getInt(
                PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB,
                PrefConst.RUNTIME_LOG_FILE_SIZE_MB_DEFAULT,
            )
            RuntimeLogStore.setEnabled(verboseLog)
            RuntimeLogStore.setMaxFileSizeMb(logFileSizeMb)
            XLog.w(
                "Diag runtime log config: verbose=%s maxFileSizeMb=%d",
                verboseLog,
                logFileSizeMb,
            )
        }
    }
}
