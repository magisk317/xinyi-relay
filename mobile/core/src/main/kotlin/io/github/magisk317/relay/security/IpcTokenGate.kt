package io.github.magisk317.relay.security

import io.github.magisk317.smscode.runtime.contract.ipc.IpcTokenMatcher

object IpcTokenGate {
    fun isAccepted(
        expectedToken: String?,
        receivedToken: String?,
    ): Boolean = IpcTokenMatcher.matches(expectedToken, receivedToken)
}
