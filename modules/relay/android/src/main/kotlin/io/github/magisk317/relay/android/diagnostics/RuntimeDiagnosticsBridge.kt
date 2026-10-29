package io.github.magisk317.relay.android.diagnostics

import android.content.Context
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.xposed.runtime.state.RuntimeDiagnosticsSnapshot
import io.github.magisk317.xposed.runtime.state.RuntimeStatusInputs
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsInstaller
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.xposed.logging.MagiskLogPolicy

object RuntimeDiagnosticsBridge {
    private const val PRODUCT = "relay"

    private val installer = RuntimeDiagnosticsInstaller {
        RuntimeDiagnosticsConfig(
            applicationId = BuildConfig.APPLICATION_ID,
            logTag = BuildConfig.LOG_TAG,
            // One token names the bundle, the staging directory and the export directory.
            product = PRODUCT,
            distributionChannel = BuildConfig.MOBILE_ENTITLEMENT_CHANNEL,
            commitHash = BuildConfig.COMMIT_HASH,
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
            return MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS
        }
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            AppPreferencesDataStore.getInt(
                context,
                RelayPrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                RelayPrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
            )
        }.let(MagiskLogPolicy::retentionDays)
    }

    private fun resolveActivationStatus(
        context: Context,
        snapshot: RuntimeDiagnosticsSnapshot,
        inputs: RuntimeStatusInputs,
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
