package io.github.magisk317.relay.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IpcTokenGateTest {
    @Test
    fun evaluate_acceptsMatchingInitializedToken() {
        val decision = IpcTokenGate.evaluate(expectedToken = "token", receivedToken = "token")

        assertTrue(decision.accepted)
        assertTrue(decision.tokenMatched)
        assertFalse(decision.compatBypassUsed)
    }

    @Test
    fun evaluate_rejectsMismatchedInitializedToken() {
        val decision = IpcTokenGate.evaluate(expectedToken = "token", receivedToken = "other")

        assertFalse(decision.accepted)
        assertFalse(decision.tokenMatched)
        assertFalse(decision.compatBypassUsed)
    }

    @Test
    fun evaluate_rejectsEmptyExpectedTokenByDefault() {
        val decision = IpcTokenGate.evaluate(expectedToken = "", receivedToken = null)

        assertFalse(decision.accepted)
        assertFalse(decision.tokenMatched)
        assertFalse(decision.compatBypassUsed)
    }

    @Test
    fun evaluate_acceptsEmptyExpectedTokenOnlyWhenCompatBypassExplicitlyAllowed() {
        val decision = IpcTokenGate.evaluate(
            expectedToken = "",
            receivedToken = null,
            allowEmptyExpectedTokenBypass = true,
        )

        assertTrue(decision.accepted)
        assertFalse(decision.tokenMatched)
        assertTrue(decision.compatBypassUsed)
    }
}
