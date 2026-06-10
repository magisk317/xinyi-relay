package io.github.magisk317.relay.sender

/**
 * Internal logging facade for sender implementations.
 * Delegates to [SenderLogger] which is wired by relay/android at init time.
 */
internal object SLog {
    fun d(tag: String, message: String) = SenderLogger.d(tag, message)
    fun i(tag: String, message: String) = SenderLogger.i(tag, message)
    fun w(tag: String, message: String) = SenderLogger.w(tag, message)
    fun w(tag: String, message: String, throwable: Throwable) = SenderLogger.w(tag, message, throwable)
    fun e(tag: String, message: String) = SenderLogger.e(tag, message)
    fun e(tag: String, message: String, throwable: Throwable) = SenderLogger.e(tag, message, throwable)
}
