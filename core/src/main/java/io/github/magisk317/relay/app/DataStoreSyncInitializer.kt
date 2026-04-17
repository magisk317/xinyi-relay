package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.prefs.AppPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DataStoreSyncInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        AppInitExecution.runWhenUserUnlocked(application, scope, "DataStoreSyncInitializer") {
            val repaired = AppPreferencesDataStore.repairKnownTypedPrefs(application)
            if (repaired > 0) {
                XLog.w("Startup pref repair applied: count=%d", repaired)
            }
            val preferenceDataSource = RuntimeGraph.from(application).preferenceDataSource
            preferenceDataSource.syncToSharedPrefs()
            preferenceDataSource.ensureReadable()

            val verboseLog = preferenceDataSource.getBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, false)
            val sensitiveDebugLog = preferenceDataSource.getBoolean(PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false)
            val logFileSizeMb = preferenceDataSource.getInt(
                PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB,
                PrefConst.RUNTIME_LOG_FILE_SIZE_MB_DEFAULT,
            )
            RuntimeLogStore.setEnabled(verboseLog)
            RuntimeLogStore.setMaxFileSizeMb(logFileSizeMb)
            SensitiveLogPolicy.setEnabled(sensitiveDebugLog)
            XLog.w(
                "Diag runtime log config: verbose=%s sensitive=%s maxFileSizeMb=%d",
                verboseLog,
                sensitiveDebugLog,
                logFileSizeMb,
            )
        }
    }
}
