package io.github.magisk317.relay.android.platform.sender

import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.sender.SenderLogSink
import io.github.magisk317.relay.sender.SenderLogSanitizer
import io.github.magisk317.relay.sender.SenderLogger

/**
 * Wires relay/sender's [SenderLogger] to relay/android's [RuntimeLogStore] and [SensitiveLogPolicy].
 * Call [install] once during app initialization.
 */
object SenderLogBridge {
    fun install() {
        SenderLogger.install(
            sink = object : SenderLogSink {
                override fun append(priority: Int, tag: String, message: String, force: Boolean, route: String?) {
                    RuntimeLogStore.append(priority, tag, message, force, route)
                }
            },
            sanitizer = object : SenderLogSanitizer {
                override fun sanitize(message: String): String =
                    SensitiveLogPolicy.sanitizeSenderLogMessage(message)
            },
        )
    }
}
