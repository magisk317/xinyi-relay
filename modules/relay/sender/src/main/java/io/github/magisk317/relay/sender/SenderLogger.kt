package io.github.magisk317.relay.sender

import android.util.Log
import io.github.magisk317.smscode.runtime.contract.logging.DefaultLogSanitizer
import io.github.magisk317.smscode.runtime.contract.logging.LogEvent
import io.github.magisk317.smscode.runtime.contract.logging.LogFormatter
import io.github.magisk317.smscode.runtime.contract.logging.LogLevel
import io.github.magisk317.smscode.runtime.contract.logging.LogSanitizer
import io.github.magisk317.smscode.runtime.contract.logging.LogSink

/**
 * Logging abstraction for sender implementations.
 * Relay-android provides the real implementation with sanitization and log storage.
 */
interface SenderLogSink : LogSink {
    override fun append(event: LogEvent) {
        append(event.priority, event.tag, event.message, event.force, event.route)
    }

    fun append(priority: Int, tag: String, message: String, force: Boolean = false, route: String? = null)

    companion object {
        const val ROUTE_SENDER = "sender"
    }
}

fun interface SenderLogSanitizer : LogSanitizer

/**
 * Default logger: writes to Android logcat only, with local sanitization and no storage.
 * Relay-android replaces this with a real implementation at init time.
 */
object SenderLogger {
    private var sink: SenderLogSink = NoopSink
    private var sanitizer: SenderLogSanitizer = DefaultSenderSanitizer

    fun install(sink: SenderLogSink, sanitizer: SenderLogSanitizer) {
        this.sink = sink
        this.sanitizer = sanitizer
    }

    fun install(sink: SenderLogSink) {
        this.sink = sink
        this.sanitizer = DefaultSenderSanitizer
    }

    fun d(tag: String, message: String) {
        log(Log.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        log(Log.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        log(Log.WARN, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        log(Log.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String) {
        log(Log.ERROR, tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        log(Log.ERROR, tag, message, throwable)
    }

    internal fun resetForTest() {
        sink = NoopSink
        sanitizer = DefaultSenderSanitizer
    }

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        val formatted = if (throwable == null) {
            message
        } else {
            LogFormatter.format(message, throwable)
        }
        val safe = sanitizer.sanitize(formatted)
        writeLogcat(priority, tag, safe, throwable)
        sink.append(
            LogEvent(
                level = LogLevel.fromPriority(priority),
                tag = tag,
                message = safe,
                route = SenderLogSink.ROUTE_SENDER,
                force = shouldForce(priority),
                sensitive = false,
            ),
        )
    }

    private fun shouldForce(priority: Int): Boolean {
        return priority >= Log.INFO
    }

    private fun writeLogcat(priority: Int, tag: String, message: String, throwable: Throwable?) {
        runCatching {
            when (priority) {
                Log.DEBUG -> Log.d(tag, message)
                Log.INFO -> Log.i(tag, message)
                Log.WARN -> if (throwable == null) Log.w(tag, message) else Log.w(tag, message, throwable)
                Log.ERROR -> if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
                else -> Log.println(priority, tag, message)
            }
        }
    }

    private object NoopSink : SenderLogSink {
        override fun append(priority: Int, tag: String, message: String, force: Boolean, route: String?) {}
    }

    private object DefaultSenderSanitizer : SenderLogSanitizer {
        override fun sanitize(message: String): String = DefaultLogSanitizer.sanitize(message)
    }
}
