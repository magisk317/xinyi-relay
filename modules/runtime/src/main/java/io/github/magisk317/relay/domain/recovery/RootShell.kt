package io.github.magisk317.relay.domain.recovery

/**
 * Public facade over the root shell executor for cross-module consumers.
 *
 * The executor itself stays internal; this wrapper exposes a stable public
 * surface without leaking internal types.
 */
object RootShell {

    /** Result of one root shell invocation. */
    data class RootShellResult(val exitCode: Int, val output: String) {

        /** True when the command exited with code 0. */
        val success: Boolean
            get() = exitCode == 0
    }

    /** Returns true when a usable root shell is available on this device. */
    fun canUseRoot(): Boolean = RootShellExecutor.canUseRoot()

    /**
     * Runs [command] through the root shell.
     *
     * @param command shell command line to execute.
     * @param timeoutMs timeout in milliseconds; converted to whole seconds internally.
     * @return a [RootShellResult] carrying the process exit code and captured output.
     */
    fun run(command: String, timeoutMs: Long): RootShellResult {
        val result = RootShellExecutor.run(command, timeoutSec = timeoutMs / 1000L)
        return RootShellResult(result.exitCode, result.output)
    }
}
