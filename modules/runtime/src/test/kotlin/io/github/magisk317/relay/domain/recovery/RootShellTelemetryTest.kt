package io.github.magisk317.relay.domain.recovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootShellTelemetryTest {

    @Test
    fun nonZeroExitIncludesExitCodeWithoutChangingReason() {
        val attributes = RootShellExecutor.rootShellTelemetryAttributes(
            source = "run",
            result = "error",
            reason = "exit_nonzero",
            durationMs = 12L,
            exitCode = 23,
        )

        assertEquals("23", attributes["exit_code"])
        assertEquals("exit_nonzero", attributes["reason"])
    }

    @Test
    fun timeoutAndExecutionFailureKeepTheirSentinelExitCodes() {
        val timeout = RootShellExecutor.rootShellTelemetryAttributes(
            source = "run",
            result = "error",
            reason = "timeout",
            durationMs = 1L,
            exitCode = -2,
        )
        val executionFailure = RootShellExecutor.rootShellTelemetryAttributes(
            source = "run",
            result = "error",
            reason = "exec_failed",
            durationMs = 1L,
            exitCode = -1,
        )

        assertEquals("-2", timeout["exit_code"])
        assertEquals("timeout", timeout["reason"])
        assertEquals("-1", executionFailure["exit_code"])
        assertEquals("exec_failed", executionFailure["reason"])
    }

    @Test
    fun nonRunEventsCanOmitExitCode() {
        val attributes = RootShellExecutor.rootShellTelemetryAttributes(
            source = "can_use_root",
            result = "skip",
            reason = "root_unavailable",
            durationMs = 3L,
        )

        assertEquals(null, attributes["exit_code"])
    }
}
