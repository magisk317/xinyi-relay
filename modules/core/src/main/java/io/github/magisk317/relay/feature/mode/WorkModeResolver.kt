package io.github.magisk317.relay.feature.mode

import android.content.Context
import io.github.magisk317.relay.android.diagnostics.ActivationDiagnosticsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WorkMode {
    Enhanced,   // Xposed hooks active
    Standard,   // System APIs only
    Inactive    // Neither working — needs permission or Xposed
}

object WorkModeResolver {
    private val _mode = MutableStateFlow(WorkMode.Inactive)
    val mode: StateFlow<WorkMode> = _mode.asStateFlow()

    fun resolve(context: Context) {
        val xposedActive = ActivationDiagnosticsStore.isModuleActivated(context)
        val permissionsGranted = StandardModePermissions.allGranted(context)
        _mode.value = when {
            xposedActive -> WorkMode.Enhanced
            permissionsGranted -> WorkMode.Standard
            else -> WorkMode.Inactive
        }
    }
}
