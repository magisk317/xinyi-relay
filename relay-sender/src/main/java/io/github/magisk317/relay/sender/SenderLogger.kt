package io.github.magisk317.relay.sender

import android.util.Log

/**
 * Logging abstraction for sender implementations.
 * Relay-android provides the real implementation with sanitization and log storage.
 */
interface SenderLogSink {
    fun append(priority: Int, tag: String, message: String, force: Boolean = false, route: String? = null)

    companion object {
        const val ROUTE_SENDER = "sender"
    }
}

interface SenderLogSanitizer {
    fun sanitize(message: String): String
}

/**
 * Default logger: writes to Android logcat only, no sanitization or storage.
 * Relay-android replaces this with a real implementation at init time.
 */
object SenderLogger {
    private var sink: SenderLogSink = NoopSink
    private var sanitizer: SenderLogSanitizer = NoopSanitizer

    fun install(sink: SenderLogSink, sanitizer: SenderLogSanitizer) {
        this.sink = sink
        this.sanitizer = sanitizer
    }

    fun d(tag: String, message: String) {
        val safe = sanitizer.sanitize(message)
        Log.d(tag, safe)
        sink.append(Log.DEBUG, tag, safe, force = true, route = SenderLogSink.ROUTE_SENDER)
    }

    fun i(tag: String, message: String) {
        val safe = sanitizer.sanitize(message)
        Log.i(tag, safe)
        sink.append(Log.INFO, tag, safe, force = true, route = SenderLogSink.ROUTE_SENDER)
    }

    fun w(tag: String, message: String) {
        val safe = sanitizer.sanitize(message)
        Log.w(tag, safe)
        sink.append(Log.WARN, tag, safe, force = true, route = SenderLogSink.ROUTE_SENDER)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        val safe = sanitizer.sanitize(message)
        Log.w(tag, safe, throwable)
        sink.append(Log.WARN, tag, "$safe\n${Log.getStackTraceString(throwable)}", force = true, route = SenderLogSink.ROUTE_SENDER)
    }

    fun e(tag: String, message: String) {
        val safe = sanitizer.sanitize(message)
        Log.e(tag, safe)
        sink.append(Log.ERROR, tag, safe, force = true, route = SenderLogSink.ROUTE_SENDER)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        val safe = sanitizer.sanitize(message)
        Log.e(tag, safe, throwable)
        sink.append(Log.ERROR, tag, "$safe\n${Log.getStackTraceString(throwable)}", force = true, route = SenderLogSink.ROUTE_SENDER)
    }

    private object NoopSink : SenderLogSink {
        override fun append(priority: Int, tag: String, message: String, force: Boolean, route: String?) {}
    }

    private object NoopSanitizer : SenderLogSanitizer {
        override fun sanitize(message: String): String = message
    }
}
