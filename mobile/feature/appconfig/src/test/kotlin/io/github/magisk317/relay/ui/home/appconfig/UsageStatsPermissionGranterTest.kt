package io.github.magisk317.relay.ui.home.appconfig

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit tests for [UsageStatsPermissionGranter] with a faked shell runner. */
class UsageStatsPermissionGranterTest {

    private class FakeRunner(
        private val canRoot: Boolean = true,
        private val canRootThrows: Boolean = false,
        private val results: Map<String, Boolean> = emptyMap(),
        private val defaultResult: Boolean = true,
    ) : ShellCommandRunner {

        val commands = mutableListOf<String>()
        val timeouts = mutableListOf<Long>()

        override fun canUseRoot(): Boolean {
            if (canRootThrows) {
                throw IllegalStateException("root probe crashed")
            }
            return canRoot
        }

        override fun run(command: String, timeoutMs: Long): ShellCommandResult {
            commands += command
            timeouts += timeoutMs
            return ShellCommandResult(success = results[command] ?: defaultResult, output = "")
        }
    }

    @Test
    fun commandStringsAreBuiltExactly() {
        assertEquals(
            "appops set com.example.app GET_USAGE_STATS allow",
            UsageStatsPermissionGranter.primaryCommand(TEST_PACKAGE),
        )
        assertEquals(
            "cmd appops set com.example.app GET_USAGE_STATS allow",
            UsageStatsPermissionGranter.fallbackCommand(TEST_PACKAGE),
        )
    }

    @Test
    fun grantTimeoutIsPassedThrough() {
        val runner = FakeRunner()
        runBlocking {
            newGranter(runner, grantTimeoutMs = 1234L).grantUsageStats(TEST_PACKAGE)
        }
        assertEquals(listOf(1234L), runner.timeouts)
    }

    @Test
    fun primarySuccessSkipsFallback() {
        val runner = FakeRunner(results = mapOf(PRIMARY_COMMAND to true))
        val granted = runBlocking {
            newGranter(runner).grantUsageStats(TEST_PACKAGE)
        }
        assertTrue(granted)
        assertEquals(listOf(PRIMARY_COMMAND), runner.commands)
    }

    @Test
    fun primaryFailureFallsBackAndSucceeds() {
        val runner = FakeRunner(
            results = mapOf(PRIMARY_COMMAND to false, FALLBACK_COMMAND to true),
        )
        val granted = runBlocking {
            newGranter(runner).grantUsageStats(TEST_PACKAGE)
        }
        assertTrue(granted)
        assertEquals(listOf(PRIMARY_COMMAND, FALLBACK_COMMAND), runner.commands)
    }

    @Test
    fun bothCommandsFailReturnsFalse() {
        val runner = FakeRunner(defaultResult = false)
        val granted = runBlocking {
            newGranter(runner).grantUsageStats(TEST_PACKAGE)
        }
        assertFalse(granted)
        assertEquals(listOf(PRIMARY_COMMAND, FALLBACK_COMMAND), runner.commands)
    }

    @Test
    fun noRootReturnsFalseWithoutRunningCommands() {
        val runner = FakeRunner(canRoot = false)
        val granted = runBlocking {
            newGranter(runner).grantUsageStats(TEST_PACKAGE)
        }
        assertFalse(granted)
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun canGrantWithRootReturnsFalseWhenProbeThrows() {
        val runner = FakeRunner(canRootThrows = true)
        val canGrant = runBlocking {
            newGranter(runner).canGrantWithRoot()
        }
        assertFalse(canGrant)
        assertTrue(runner.commands.isEmpty())
    }

    private fun newGranter(
        runner: ShellCommandRunner,
        grantTimeoutMs: Long = UsageStatsPermissionGranter.DEFAULT_GRANT_TIMEOUT_MS,
    ): UsageStatsPermissionGranter = UsageStatsPermissionGranter(
        runner = runner,
        ioDispatcher = Dispatchers.Unconfined,
        grantTimeoutMs = grantTimeoutMs,
    )

    private companion object {

        private const val TEST_PACKAGE = "com.example.app"

        private val PRIMARY_COMMAND =
            UsageStatsPermissionGranter.primaryCommand(TEST_PACKAGE)

        private val FALLBACK_COMMAND =
            UsageStatsPermissionGranter.fallbackCommand(TEST_PACKAGE)
    }
}
