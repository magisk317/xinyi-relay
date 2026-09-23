package io.github.magisk317.relay.feature.mode

import android.content.Context
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WorkMode {
    Enhanced,   // Xposed hooks active
    Standard,   // Legacy: system-API-only channel, no longer resolved
    Inactive    // No active runtime (module not activated)
}

object WorkModeResolver {
    private val _mode = MutableStateFlow(WorkMode.Inactive)
    val mode: StateFlow<WorkMode> = _mode.asStateFlow()

    fun resolve(context: Context): WorkMode {
        val xposedActive = ActivationDiagnosticsStore.isModuleActivated(context)
        // The standard (system-API-only) channel is gone: without an active Xposed
        // runtime there is nothing to forward through.
        val resolved = if (xposedActive) WorkMode.Enhanced else WorkMode.Inactive
        _mode.value = resolved
        return resolved
    }
}
