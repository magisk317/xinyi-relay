package io.github.magisk317.relay.security

data class IpcTokenGateDecision(
    val accepted: Boolean,
    val tokenMatched: Boolean,
    val compatBypassUsed: Boolean,
)

object IpcTokenGate {
    fun evaluate(expectedToken: String?, receivedToken: String?): IpcTokenGateDecision {
        val expected = expectedToken.orEmpty()
        if (expected.isBlank()) {
            return IpcTokenGateDecision(
                accepted = true,
                tokenMatched = false,
                compatBypassUsed = true,
            )
        }
        val matched = receivedToken == expected
        return IpcTokenGateDecision(
            accepted = matched,
            tokenMatched = matched,
            compatBypassUsed = false,
        )
    }
}
