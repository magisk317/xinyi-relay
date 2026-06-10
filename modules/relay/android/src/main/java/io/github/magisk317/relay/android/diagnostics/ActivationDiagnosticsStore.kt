package io.github.magisk317.relay.android.diagnostics

import android.content.Context
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsSnapshot as SharedActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore as SharedActivationDiagnosticsStore
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationStatusState as SharedActivationStatusState
import kotlinx.coroutines.flow.StateFlow

typealias ActivationDiagnosticsSnapshot = SharedActivationDiagnosticsSnapshot
typealias ActivationStatusState = SharedActivationStatusState

object ActivationDiagnosticsStore {
    fun snapshot(context: Context): ActivationDiagnosticsSnapshot {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedActivationDiagnosticsStore.snapshot(context)
    }

    fun hasHookHeartbeatThisBoot(context: Context): Boolean {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedActivationDiagnosticsStore.hasHookHeartbeatThisBoot(context)
    }

    fun isRuntimeConnected(): Boolean {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedActivationDiagnosticsStore.isRuntimeConnected()
    }

    fun isModuleActivated(context: Context): Boolean {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedActivationDiagnosticsStore.isModuleActivated(context)
    }

    fun observeStatus(context: Context): StateFlow<ActivationStatusState> {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedActivationDiagnosticsStore.observeStatus(context)
    }

    fun recordServiceBind(
        context: Context,
        frameworkName: String,
        frameworkVersion: String,
        verboseLogging: Boolean,
    ) {
        RuntimeDiagnosticsBridge.ensureInstalled()
        SharedActivationDiagnosticsStore.recordServiceBind(context, frameworkName, frameworkVersion, verboseLogging)
    }

    fun recordServiceDied(
        context: Context,
        verboseLogging: Boolean,
    ) {
        RuntimeDiagnosticsBridge.ensureInstalled()
        SharedActivationDiagnosticsStore.recordServiceDied(context, verboseLogging)
    }

    fun recordHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
        route: String = RuntimeLogStore.ROUTE_SMS_HOOK,
    ) {
        RuntimeDiagnosticsBridge.ensureInstalled()
        SharedActivationDiagnosticsStore.recordHookHeartbeat(
            context = context,
            packageName = packageName,
            processName = processName,
            source = source,
            verboseLogging = verboseLogging,
            route = route,
        )
    }
}
