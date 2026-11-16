package io.github.magisk317.relay.feature.mode

/**
 * Decides whether the NonRoot runtime is running.
 *
 * The runtime runs only in a build that enables NonRoot mode, and only while
 * the notification listener reports that it is connected. The result does not
 * depend on the Xposed runtime.
 */
object NonRootRuntimeRule {

    /** True when NonRoot mode is enabled in this build and the listener is connected. */
    fun isRunning(enabledInBuild: Boolean, listenerConnected: Boolean): Boolean =
        enabledInBuild && listenerConnected
}
