package com.github.magisk317.smscode.forwarder.recovery

import com.github.magisk317.smscode.common.utils.XLog
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
        val result = run("id -u", timeoutSec = 5)
        return result.success && result.output.trim() == "0"
    }

    fun hasSqlite3(): Boolean {
        val result = run("command -v sqlite3 >/dev/null 2>&1", timeoutSec = 5)
        return result.success
    }

    fun run(command: String, timeoutSec: Long = DEFAULT_TIMEOUT_SEC): ShellResult {
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
            XLog.w(
                "RootShellExecutor run failed: %s",
                e.message ?: e.javaClass.simpleName,
            )
            ShellResult(
                exitCode = -1,
                output = "",
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            XLog.w(
                "RootShellExecutor run interrupted: %s",
                e.message ?: e.javaClass.simpleName,
            )
            ShellResult(
                exitCode = -1,
                output = "",
            )
        } catch (e: SecurityException) {
            XLog.w(
                "RootShellExecutor run denied: %s",
                e.message ?: e.javaClass.simpleName,
            )
            ShellResult(
                exitCode = -1,
                output = "",
            )
        }
    }
}
