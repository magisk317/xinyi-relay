package io.github.magisk317.relay.app

import android.app.Application
import android.content.SharedPreferences
import io.github.magisk317.relay.android.common.utils.RelayLogger
import io.github.magisk317.relay.android.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.android.diagnostics.RuntimeActivationState
import io.github.magisk317.relay.android.prefs.AppPreferencesDataStore
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object XposedServiceRuntimeCoordinator {
    fun handleServiceBound(
        application: Application,
        applicationScope: CoroutineScope,
        remotePrefsProvider: (() -> SharedPreferences?)?,
        frameworkName: String?,
        frameworkVersion: String?,
    ) {
        AppPreferencesDataStore.setRemotePrefsProvider(remotePrefsProvider)
        val pending = AppPreferencesDataStore.hasPendingRemoteSync()
        applicationScope.launch {
            HookPreferenceMirror.publish(application)
            if (pending) {
                RelayLogger.w(LogRoute.APP, "RemotePrefs sync pending after bind; retrying once")
                AppPreferencesDataStore.syncToRemotePrefs(application)
            }
        }
        val verboseLogEnabled = PrefsReader.isVerboseLogMode(application)
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
    }

    fun handleServiceDied(application: Application) {
        AppPreferencesDataStore.setRemotePrefsProvider(null)
        RuntimeActivationState.setRuntimeActivated(false)
        ActivationDiagnosticsStore.recordServiceDied(
            context = application,
            verboseLogging = PrefsReader.isVerboseLogMode(application),
        )
        RelayLogger.w(LogRoute.APP, "Xposed service disconnected")
    }

    fun logRegistrationFailure(throwable: Throwable) {
        RelayLogger.w(LogRoute.APP, "Failed to register Xposed service listener: %s", throwable.message ?: "unknown")
    }
}
