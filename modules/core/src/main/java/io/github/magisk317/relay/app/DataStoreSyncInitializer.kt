package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.android.common.utils.RelayLogger
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.prefs.AppPreferencesDataStore
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DataStoreSyncInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        AppInitExecution.runWhenUserUnlocked(application, scope, "DataStoreSyncInitializer") {
            val repaired = AppPreferencesDataStore.repairKnownTypedPrefs(application)
            if (repaired > 0) {
                RelayLogger.w(LogRoute.APP, "Startup pref repair applied: count=%d", repaired)
            }
            val imported = AppPreferencesDataStore.importMissingSharedPrefsIntoDataStore(application)
            if (imported > 0) {
                RelayLogger.w(LogRoute.APP, "Startup shared-pref import applied: count=%d", imported)
            }
            HookPreferenceMirror.publish(application)

            val preferenceDataSource = RuntimeGraph.from(application).preferenceDataSource

            val verboseLog = preferenceDataSource.getBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, false)
            val sensitiveDebugLog = preferenceDataSource.getBoolean(PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false)
            val logRetentionDays = preferenceDataSource.getInt(
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
            )
            RuntimeLogStore.setEnabled(verboseLog)
            RuntimeLogStore.setRetentionDays(logRetentionDays)
            SensitiveLogPolicy.setEnabled(sensitiveDebugLog)
            RelayLogger.w(
                LogRoute.APP,
                "Diag runtime log config: verbose=%s sensitive=%s retentionDays=%d",
                verboseLog,
                sensitiveDebugLog,
                logRetentionDays,
            )
        }
    }
}
