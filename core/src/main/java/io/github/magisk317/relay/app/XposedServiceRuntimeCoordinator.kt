package io.github.magisk317.relay.app

import android.app.Application
import android.content.SharedPreferences
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.android.diagnostics.RuntimeActivationState
import io.github.magisk317.relay.prefs.AppPreferencesDataStore
import io.github.magisk317.relay.prefs.HookPreferenceMirror
import io.github.magisk317.relay.prefs.PrefsReader
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
            if (pending) {
                XLog.w("RemotePrefs sync pending detected; attempting sync on service bind")
            }
            HookPreferenceMirror.publish(application)
        }
        val verboseLogEnabled = PrefsReader.isVerboseLogMode(application)
        RuntimeActivationState.setRuntimeActivated(true)
        ActivationDiagnosticsStore.recordServiceBind(
            context = application,
            frameworkName = frameworkName ?: "unknown",
            frameworkVersion = frameworkVersion ?: "unknown",
            verboseLogging = verboseLogEnabled,
        )
        XLog.i(
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
        XLog.w("Xposed service disconnected")
    }

    fun logRegistrationFailure(throwable: Throwable) {
        XLog.w("Failed to register Xposed service listener: %s", throwable.message ?: "unknown")
    }
}
