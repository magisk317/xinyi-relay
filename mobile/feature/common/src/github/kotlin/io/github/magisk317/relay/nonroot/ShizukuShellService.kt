package io.github.magisk317.relay.nonroot

import android.os.Bundle
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit

/**
 * Shizuku user service: Shizuku constructs this class in its own process with
 * the shell uid, so it is a Binder stub rather than an Android Service.
 */
@Keep
class ShizukuShellService : IShizukuShellService.Stub() {

    override fun run(command: String): Bundle {
        return runCommand(command)
    }

    private fun runCommand(command: String): Bundle {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(output) { output.appendLine(line) }
                        }
                    }
                } catch (_: Exception) {
                    // The exit code remains the source of truth if the pipe closes early.
                }
            }.apply {
                isDaemon = true
                start()
            }
            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return result(exitCode = -1, output = "timed out: $command")
            }
            reader.join(500)
            val exit = process.exitValue()
            val text = synchronized(output) { output.toString().trim() }
            result(exitCode = exit, output = text)
        } catch (error: Exception) {
            result(exitCode = -1, output = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun result(exitCode: Int, output: String): Bundle {
        return Bundle().apply {
            putInt(KEY_EXIT, exitCode)
            putString(KEY_OUTPUT, output.take(MAX_LOG_CHARS))
        }
    }

    companion object {
        const val KEY_EXIT = "exit"
        const val KEY_OUTPUT = "output"
        private const val COMMAND_TIMEOUT_SECONDS = 10L
        private const val MAX_LOG_CHARS = 500
    }
}
