package io.github.magisk317.relay.feature.mode

import android.content.Context
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WorkMode {
    Enhanced,   // Xposed hooks active
    Standard,   // System APIs only
    Inactive    // Reserved for an explicit global disable state
}

object WorkModeResolver {
    private val _mode = MutableStateFlow(WorkMode.Inactive)
    val mode: StateFlow<WorkMode> = _mode.asStateFlow()

    fun resolve(context: Context): WorkMode {
        val xposedActive = ActivationDiagnosticsStore.isModuleActivated(context)
        val resolved = if (xposedActive) WorkMode.Enhanced else WorkMode.Standard
        _mode.value = resolved
        return resolved
    }
}
