package io.github.magisk317.relay.android.diagnostics

import android.content.Context
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationStatusInputs
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsInstaller
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore

object RuntimeDiagnosticsBridge {
    private const val KEY_RUNTIME_LOG_RETENTION_DAYS = "pref_runtime_log_retention_days"
    private const val RUNTIME_LOG_RETENTION_DAYS_DEFAULT = 7
    private const val RUNTIME_LOG_RETENTION_DAYS_MIN = 1

    private val installer = RuntimeDiagnosticsInstaller {
        RuntimeDiagnosticsConfig(
            applicationId = BuildConfig.APPLICATION_ID,
            logTag = BuildConfig.LOG_TAG,
            exportFilePrefix = "relay_logs_",
            stagingDirPrefix = ".tmp_relay_logs_",
            logRetentionDaysProvider = ::readConfiguredLogRetentionDays,
            runtimeConnectedProvider = RuntimeActivationState::isRuntimeActivated,
            activationStatusResolver = ::resolveActivationStatus,
            routeResolver = ::routeFromCallerClassName,
        )
    }

    fun ensureInstalled() = installer.ensureInstalled()

    private fun readConfiguredLogRetentionDays(context: Context): Int {
        // RuntimeLogStore may invoke this callback with a target-process context when the
        // provider route is unavailable. Never interpret that process's private DataStore as
        // module configuration.
        if (context.packageName != BuildConfig.APPLICATION_ID) {
            return RUNTIME_LOG_RETENTION_DAYS_DEFAULT
        }
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            AppPreferencesDataStore.getInt(context, KEY_RUNTIME_LOG_RETENTION_DAYS, RUNTIME_LOG_RETENTION_DAYS_DEFAULT)
        }.coerceAtLeast(RUNTIME_LOG_RETENTION_DAYS_MIN)
    }

    private fun resolveActivationStatus(
        context: Context,
        snapshot: ActivationDiagnosticsSnapshot,
        inputs: ActivationStatusInputs,
    ): Boolean {
        return inputs.runtimeConnected || inputs.hasHookHeartbeat
    }

    private fun routeFromCallerClassName(className: String?): String {
        val value = className.orEmpty()
        return when {
            value.contains(".xp.hook.forward.") -> RuntimeLogStore.ROUTE_FORWARD
            value.contains(".xp.hook.code.") -> RuntimeLogStore.ROUTE_SMS_HOOK
            value.contains(".xp.hook.telephony.") -> RuntimeLogStore.ROUTE_SMS_HOOK
            value.contains(".xp.LibXposedEntry") -> RuntimeLogStore.ROUTE_SMS_HOOK
            value.contains(".xp.hook.notification.") ||
                value.contains(".core.hook.notification.") ||
                value.contains(".xposed.hook.notification.") -> RuntimeLogStore.ROUTE_NMS_HOOK
            value.contains(".xp.hook.system.") ||
                value.contains(".core.hook.system.") ||
                value.contains(".xposed.hook.system.") -> RuntimeLogStore.ROUTE_SYSTEM_INPUT
            value.contains(".xp.hook.permission.") ||
                value.contains(".core.hook.permission.") ||
                value.contains(".xposed.hook.permission.") -> RuntimeLogStore.ROUTE_PERMISSION_HOOK
            value.contains(".domain.recovery.") || value.contains(".forwarder.recovery.") -> RuntimeLogStore.ROUTE_ROOT_DB
            else -> RuntimeLogStore.ROUTE_APP
        }
    }
}
