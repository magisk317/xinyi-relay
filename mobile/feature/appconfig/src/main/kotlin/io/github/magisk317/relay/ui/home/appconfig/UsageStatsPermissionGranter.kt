package io.github.magisk317.relay.ui.home.appconfig

import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.domain.recovery.RootShell
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of a single shell command invocation. */
data class ShellCommandResult(val success: Boolean, val output: String = "")

/** Abstraction over a root shell so command execution can be faked in tests. */
interface ShellCommandRunner {

    /** Returns true when the device can execute commands through a root shell. */
    fun canUseRoot(): Boolean

    /**
     * Runs [command] with the given [timeoutMs] and returns the outcome.
     */
    fun run(command: String, timeoutMs: Long): ShellCommandResult
}

/**
 * Grants the usage stats access permission through a root shell.
 *
 * The primary `appops set` command runs first; if it fails, the `cmd appops set`
 * fallback runs exactly once. All shell interaction happens on [ioDispatcher].
 */
internal class UsageStatsPermissionGranter(
    private val runner: ShellCommandRunner = RootShellRunner(),
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
    private val grantTimeoutMs: Long = DEFAULT_GRANT_TIMEOUT_MS,
) {

    /** Returns true when this device can grant the permission through root. */
    suspend fun canGrantWithRoot(): Boolean = withContext(ioDispatcher) {
        try {
            runner.canUseRoot()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // A failed root probe degrades to the system-settings channel
            // instead of crashing the dialog on open.
            XLog.w("Failed to probe root for usage stats permission grant", e)
            false
        }
    }

    /**
     * Grants usage stats access for [packageName] through a root shell.
     *
     * @return true when the primary command succeeds, or when the fallback
     *   succeeds after the primary fails; false when root is unavailable or
     *   both commands fail.
     */
    suspend fun grantUsageStats(packageName: String): Boolean = withContext(ioDispatcher) {
        if (!runner.canUseRoot()) {
            return@withContext false
        }
        if (runOnce(primaryCommand(packageName))) {
            true
        } else {
            runOnce(fallbackCommand(packageName))
        }
    }

    private suspend fun runOnce(command: String): Boolean = withContext(ioDispatcher) {
        runner.run(command, grantTimeoutMs).success
    }

    companion object {

        /** Per-command timeout for root shell invocations. */
        const val DEFAULT_GRANT_TIMEOUT_MS = 10_000L

        /** Primary grant command through the `appops` binary. */
        fun primaryCommand(packageName: String): String =
            "appops set $packageName GET_USAGE_STATS allow"

        /** Fallback grant command through the `cmd` shim. */
        fun fallbackCommand(packageName: String): String =
            "cmd appops set $packageName GET_USAGE_STATS allow"
    }
}

/** Production [ShellCommandRunner] backed by the [RootShell] facade. */
private class RootShellRunner : ShellCommandRunner {

    override fun canUseRoot(): Boolean = RootShell.canUseRoot()

    override fun run(command: String, timeoutMs: Long): ShellCommandResult = try {
        val result = RootShell.run(command, timeoutMs)
        ShellCommandResult(result.success, result.output)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        XLog.e("Usage stats grant command failed: $command", e)
        ShellCommandResult(false, e.message.orEmpty())
    }
}
