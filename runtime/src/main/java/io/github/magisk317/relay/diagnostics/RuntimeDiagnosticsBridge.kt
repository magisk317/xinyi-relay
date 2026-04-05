package io.github.magisk317.relay.diagnostics

import android.content.Context
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationStatusInputs
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsEnvironment

internal object RuntimeDiagnosticsBridge {
    @Volatile
    private var installed = false

    fun ensureInstalled() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            RuntimeDiagnosticsEnvironment.install(
                RuntimeDiagnosticsConfig(
                    applicationId = BuildConfig.APPLICATION_ID,
                    logTag = BuildConfig.LOG_TAG,
                    exportFilePrefix = "relay_logs_",
                    stagingDirPrefix = ".tmp_relay_logs_",
                    maxLogFileSizeMbProvider = ::readConfiguredMaxFileSizeMb,
                    runtimeConnectedProvider = RuntimeActivationState::isRuntimeActivated,
                    activationStatusResolver = ::resolveActivationStatus,
                    routeResolver = ::routeFromCallerClassName,
                ),
            )
            installed = true
        }
    }

    private fun readConfiguredMaxFileSizeMb(context: Context): Int {
        val defaultValue = PrefConst.RUNTIME_LOG_FILE_SIZE_MB_DEFAULT
        val prefs = runCatching {
            context.getSharedPreferences("xposed_prefs", Context.MODE_PRIVATE)
        }.getOrNull() ?: return defaultValue
        val raw = prefs.all[PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB]
        val value = when (raw) {
            is Int -> raw
            is Long -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> defaultValue
        } ?: defaultValue
        return value.coerceAtLeast(PrefConst.RUNTIME_LOG_FILE_SIZE_MB_MIN)
    }

    private fun resolveActivationStatus(
        context: Context,
        snapshot: ActivationDiagnosticsSnapshot,
        inputs: ActivationStatusInputs,
    ): Boolean {
        return inputs.runtimeConnected || inputs.hasHookHeartbeat || inputs.hasLegacyActivationMarker
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
