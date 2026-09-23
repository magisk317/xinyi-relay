package io.github.magisk317.relay.app

import android.app.Application
import android.content.SharedPreferences
import io.github.magisk317.relay.android.common.utils.RelayLogger
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.android.diagnostics.RuntimeActivationState
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.github.magisk317.xposed.logging.MagiskOtel

object XposedServiceRuntimeCoordinator {
    fun handleServiceBound(
        application: Application,
        applicationScope: CoroutineScope,
        remotePrefsProvider: (() -> SharedPreferences?)?,
        frameworkName: String?,
        frameworkVersion: String?,
    ): WorkMode {
        AppPreferencesDataStore.setRemotePrefsProvider(remotePrefsProvider)
        val pending = AppPreferencesDataStore.hasPendingRemotePrefsPublish()
        applicationScope.launch {
            HookPreferenceMirror.publish(application)
            if (pending) {
                RelayLogger.w(LogRoute.APP, "RemotePrefs sync pending after bind; retrying once")
                AppPreferencesDataStore.syncToRemotePrefs(application)
            }
        }
        val verboseLogEnabled = readVerboseLogMode(application)
        RuntimeActivationState.setRuntimeActivated(true)
        ActivationDiagnosticsStore.recordServiceBind(
            context = application,
            frameworkName = frameworkName ?: "unknown",
            frameworkVersion = frameworkVersion ?: "unknown",
            verboseLogging = verboseLogEnabled,
        )
        RelayLogger.i(
            LogRoute.APP,
            "Xposed service connected: framework=%s version=%s",
            frameworkName ?: "unknown",
            frameworkVersion ?: "unknown",
        )
        MagiskOtel.event(
            name = "hook.service",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "bound",
                "reason" to (frameworkName ?: "unknown"),
            ),
            statusOk = true,
        )
        return WorkModeResolver.resolve(application)
    }

    fun handleServiceDied(application: Application): WorkMode {
        AppPreferencesDataStore.setRemotePrefsProvider(null)
        RuntimeActivationState.setRuntimeActivated(false)
        ActivationDiagnosticsStore.recordServiceDied(
            context = application,
            verboseLogging = readVerboseLogMode(application),
        )
        RelayLogger.w(LogRoute.APP, "Xposed service disconnected")
        MagiskOtel.event(
            name = "hook.service",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "died",
            ),
            statusOk = true,
        )
        return WorkModeResolver.resolve(application)
    }

    fun logRegistrationFailure(throwable: Throwable) {
        RelayLogger.w(LogRoute.APP, "Failed to register Xposed service listener: %s", throwable.message ?: "unknown")
        MagiskOtel.event(
            name = "hook.service",
            attributes = mapOf(
                "result" to "error",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "register",
                "reason" to throwable.javaClass.simpleName,
            ),
            statusOk = false,
        )
    }

    private fun readVerboseLogMode(application: Application): Boolean = runBlocking(Dispatchers.IO) {
        AppPreferencesDataStore.getBoolean(application, PrefConst.KEY_VERBOSE_LOG_MODE, false)
    }
}
