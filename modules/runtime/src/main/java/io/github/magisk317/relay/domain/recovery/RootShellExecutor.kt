package io.github.magisk317.relay.domain.recovery

import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.xposed.logging.MagiskOtel
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object RootShellExecutor {

    internal data class ShellResult(
        val exitCode: Int,
        val output: String,
    ) {
        val success: Boolean
            get() = exitCode == 0
    }

    private const val DEFAULT_TIMEOUT_SEC = 10L

    fun canUseRoot(): Boolean {
        val startedAt = System.nanoTime()
        val result = execute("id -u", timeoutSec = 5)
        val usable = result.success && result.output.trim() == "0"
        emitRootShell(
            source = "can_use_root",
            result = if (usable) "ok" else "skip",
            reason = if (usable) "root_available" else "root_unavailable",
            durationMs = elapsedMs(startedAt),
            statusOk = true,
        )
        return usable
    }

    fun hasSqlite3(): Boolean {
        val startedAt = System.nanoTime()
        val result = execute("command -v sqlite3 >/dev/null 2>&1", timeoutSec = 5)
        emitRootShell(
            source = "has_sqlite3",
            result = if (result.success) "ok" else "skip",
            reason = if (result.success) "sqlite3_present" else "sqlite3_missing",
            durationMs = elapsedMs(startedAt),
            statusOk = true,
        )
        return result.success
    }

    fun run(command: String, timeoutSec: Long = DEFAULT_TIMEOUT_SEC): ShellResult {
        val startedAt = System.nanoTime()
        val result = execute(command, timeoutSec)
        val reason = when (result.exitCode) {
            0 -> "exit_ok"
            -2 -> "timeout"
            -1 -> "exec_failed"
            else -> "exit_nonzero"
        }
        emitRootShell(
            source = "run",
            result = if (result.success) "ok" else "error",
            reason = reason,
            durationMs = elapsedMs(startedAt),
            statusOk = result.success,
        )
        return result
    }

    private fun execute(command: String, timeoutSec: Long): ShellResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                return ShellResult(
                    exitCode = -2,
                    output = "",
                )
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            ShellResult(
                exitCode = process.exitValue(),
                output = output,
            )
        } catch (e: IOException) {
            XLog.w("run failed: %s", e.message ?: e.javaClass.simpleName)
            ShellResult(
                exitCode = -1,
                output = "",
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            XLog.w("run interrupted: %s", e.message ?: e.javaClass.simpleName)
            ShellResult(
                exitCode = -1,
                output = "",
            )
        } catch (e: SecurityException) {
            XLog.w("run denied: %s", e.message ?: e.javaClass.simpleName)
            ShellResult(
                exitCode = -1,
                output = "",
            )
        }
    }

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    private fun emitRootShell(
        source: String,
        result: String,
        reason: String,
        durationMs: Long,
        statusOk: Boolean,
    ) {
        MagiskOtel.event(
            name = "app.recovery",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "main",
                "stage" to "root_shell",
                "reason" to reason,
                "source" to source,
            ),
            statusOk = statusOk,
        )
    }
}
