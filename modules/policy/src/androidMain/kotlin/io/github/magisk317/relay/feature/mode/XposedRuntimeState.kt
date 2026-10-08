package io.github.magisk317.relay.feature.mode

import android.content.Context
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the Xposed runtime is currently live.
 *
 * Xposed is the only runtime path: with no active hook runtime there is
 * nothing to forward through, so every Xposed-backed feature stays off. The
 * state is published as a plain boolean so callers cannot keep re-deriving
 * the answer from an enum that no longer says anything.
 */
object XposedRuntimeState {
    private val _xposedActive = MutableStateFlow(false)
    val xposedActive: StateFlow<Boolean> = _xposedActive.asStateFlow()

    /** Recomputes the activation state, publishes it, and returns the fresh value. */
    fun refresh(context: Context): Boolean {
        val active = ActivationDiagnosticsStore.isModuleActivated(context)
        _xposedActive.value = active
        return active
    }
}
