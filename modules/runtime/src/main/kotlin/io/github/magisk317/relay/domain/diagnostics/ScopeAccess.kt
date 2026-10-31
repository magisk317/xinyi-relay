package io.github.magisk317.relay.domain.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live LSPosed scope as seen by the settings process. */
data class XposedScopeState(
    val connected: Boolean = false,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val packages: List<String> = emptyList(),
    val refreshedAtMs: Long = 0L,
) {
    val hasSystemScope: Boolean
        get() = packages.any { it == "android" || it == "system" }
}

sealed interface ScopeRequestOutcome {
    data class Approved(val packages: List<String>) : ScopeRequestOutcome
    data class Rejected(val message: String) : ScopeRequestOutcome
    data class Failed(val message: String) : ScopeRequestOutcome
    data class Unavailable(val message: String) : ScopeRequestOutcome
}

/**
 * The settings UI cannot touch the libxposed service binder directly: it lives in the
 * xposed-flavored application code. The app installs this access object once and the
 * diagnostics page goes through it.
 */
interface XposedScopeAccess {
    fun refresh()

    fun requestMissing(packages: List<String>, onResult: (ScopeRequestOutcome) -> Unit)
}

object XposedScopeAccessHolder {
    private val _state = MutableStateFlow(XposedScopeState())
    val state: StateFlow<XposedScopeState> = _state.asStateFlow()

    @Volatile
    private var access: XposedScopeAccess? = null

    fun install(access: XposedScopeAccess?) {
        this.access = access
    }

    fun current(): XposedScopeAccess? = access

    fun update(newState: XposedScopeState) {
        _state.value = newState
    }
}
