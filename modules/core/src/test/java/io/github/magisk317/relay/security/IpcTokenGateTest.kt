package io.github.magisk317.relay.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IpcTokenGateTest {
    @Test
    fun isAccepted_acceptsMatchingInitializedToken() {
        assertTrue(IpcTokenGate.isAccepted(expectedToken = "token", receivedToken = "token"))
    }

    @Test
    fun isAccepted_rejectsMismatchedInitializedToken() {
        assertFalse(IpcTokenGate.isAccepted(expectedToken = "token", receivedToken = "other"))
    }

    @Test
    fun isAccepted_rejectsEmptyExpectedToken() {
        assertFalse(IpcTokenGate.isAccepted(expectedToken = "", receivedToken = null))
    }

    @Test
    fun isAccepted_rejectsBlankTokens() {
        assertFalse(IpcTokenGate.isAccepted(expectedToken = " ", receivedToken = " "))
    }
}
