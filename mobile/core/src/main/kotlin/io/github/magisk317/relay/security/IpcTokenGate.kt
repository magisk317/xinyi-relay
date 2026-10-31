package io.github.magisk317.forward.security

import io.github.magisk317.xposed.runtime.ipc.IpcTokenMatcher

object IpcTokenGate {
    fun isAccepted(
        expectedToken: String?,
        receivedToken: String?,
    ): Boolean = IpcTokenMatcher.matches(expectedToken, receivedToken)
}
