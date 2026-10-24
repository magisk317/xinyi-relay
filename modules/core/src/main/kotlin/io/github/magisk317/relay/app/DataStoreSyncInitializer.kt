package io.github.magisk317.relay.app

import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import android.app.Application
import io.github.magisk317.relay.android.common.utils.RelayLogger
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.xposed.logging.LogSanitizerConfig

class DataStoreSyncInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        AppInitExecution.runWhenUserUnlocked(application, scope, "DataStoreSyncInitializer") {
            HookPreferenceMirror.publish(application)

            val preferenceDataSource = RuntimeGraph.from(application).preferenceDataSource

            val isPlaintextBuild = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD
            val verboseLog = isPlaintextBuild
            val logRetentionDays = preferenceDataSource.getInt(
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
            )
            RuntimeDiagnosticsBridge.ensureInstalled()
            RuntimeLogStore.setEnabled(verboseLog)
            RuntimeDiagnosticsBridge.ensureInstalled()
            RuntimeLogStore.setRetentionDays(logRetentionDays)
            LogSanitizerConfig.syncFromVerboseMode(isPlaintextBuild)
            RelayLogger.w(
                LogRoute.APP,
                "Diag runtime log config: verbose=%s retentionDays=%d",
                verboseLog,
                logRetentionDays,
            )
            MagiskOtel.event(
                name = "prefs.access",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "startup_sync",
                    "reason" to "user_unlocked",
                    "change_count" to "0",
                ),
                statusOk = true,
            )
        }
    }
}
