package io.github.magisk317.relay.android.diagnostics

object RuntimeActivationState {
    @Volatile
    private var runtimeActivated: Boolean = false

    fun setRuntimeActivated(activated: Boolean) {
        runtimeActivated = activated
    }

    fun isRuntimeActivated(): Boolean = runtimeActivated
}
