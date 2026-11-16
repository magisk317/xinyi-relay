package io.github.magisk317.relay.nonroot

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

internal object ShizukuShellClient {
    private const val BIND_TIMEOUT_MS = 20_000L
    private const val RUN_TIMEOUT_MS = 120_000L

    suspend fun run(context: Context, commands: List<String>, log: (String) -> Unit): Boolean {
        if (commands.isEmpty()) return true
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            log("Shizuku binder is not available")
            return false
        }
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuShellService::class.java.name),
        )
            .processNameSuffix("shell")
            .version(1)
            .daemon(false)
        var connection: ServiceConnection? = null

        val binder = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine<IBinder?> { continuation ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        if (continuation.isActive) continuation.resume(service)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                connection = conn
                runCatching {
                    Shizuku.bindUserService(args, conn)
                }.onFailure { error ->
                    log("Shizuku user service bind failed: ${error.message ?: error.javaClass.simpleName}")
                    if (continuation.isActive) continuation.resume(null)
                }
                continuation.invokeOnCancellation {
                    connection?.let { bound -> unbind(args, bound) }
                    connection = null
                }
            }
        }

        if (binder == null) {
            log("Shizuku user service did not bind")
            connection?.let { unbind(args, it) }
            connection = null
            return false
        }

        return try {
            withTimeoutOrNull(RUN_TIMEOUT_MS) {
                val shell = IShizukuShellService.Stub.asInterface(binder)
                var allSucceeded = true
                for (command in commands) {
                    val result = runCatching { shell.run(command) }.getOrNull()
                    if (result == null) {
                        log("$command -> failed")
                        allSucceeded = false
                        continue
                    }
                    val exit = result.getInt(ShizukuShellService.KEY_EXIT, -1)
                    val output = result.getString(ShizukuShellService.KEY_OUTPUT).orEmpty()
                    val detail = buildString {
                        append("$command -> exit=$exit")
                        if (output.isNotBlank()) append(": ").append(output)
                    }
                    log(detail)
                    if (exit != 0) allSucceeded = false
                }
                allSucceeded
            } ?: run {
                log("Shizuku shell timed out")
                false
            }
        } finally {
            connection?.let { unbind(args, it) }
            connection = null
        }
    }

    private fun unbind(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (_: Exception) {
            // The service may already be gone.
        }
    }
}
