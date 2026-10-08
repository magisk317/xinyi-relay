package io.github.magisk317.relay.android.common.utils

import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.smscode.runtime.common.utils.StringUtils
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.LogSanitizerConfig
import io.github.magisk317.xposed.logging.SecretRedactor

object SensitiveLogPolicy {
    private const val ENABLED_LOG_MAX_LENGTH = 1200

    @Volatile
    private var enabled = false

    @JvmStatic
    fun isSupported(): Boolean = BuildConfig.DEBUG

    @JvmStatic
    fun isEnabled(): Boolean = isSupported() && enabled

    @JvmStatic
    fun setEnabled(value: Boolean) {
        enabled = isSupported() && value
        LogSanitizerConfig.syncFromVerboseMode(isEnabled())
    }

    @JvmStatic
    fun sanitizeLogMessage(message: String): String {
        if (isEnabled()) {
            return truncate(SecretRedactor.redact(message), ENABLED_LOG_MAX_LENGTH)
        }
        return DefaultLogSanitizer.sanitize(message)
    }

    @JvmStatic
    fun sanitizeSenderLogMessage(message: String): String {
        var sanitized = SecretRedactor.redact(message)
        if (isEnabled()) {
            return truncate(sanitized, ENABLED_LOG_MAX_LENGTH)
        }
        sanitized = summarizeAfterLabel(sanitized, "requestMsg:")
        sanitized = summarizeAfterLabel(sanitized, "Response:")
        sanitized = summarizeAfterLabel(sanitized, "response unexpected:")
        sanitized = summarizeAfterLabel(sanitized, "send failed:")
        sanitized = summarizeAfterLabel(sanitized, "API failed:")
        sanitized = summarizeAfterLabel(sanitized, "Get token failed:")
        sanitized = summarizeAfterLabel(sanitized, "Fetch token failed:")
        return DefaultLogSanitizer.sanitize(sanitized)
    }

    private fun summarizeAfterLabel(message: String, label: String): String {
        val index = message.indexOf(label, ignoreCase = true)
        if (index < 0) return message
        val start = index + label.length
        return message.substring(0, start) + StringUtils.summarizePayload(message.substring(start).trim())
    }

    private fun truncate(value: String, max: Int): String {
        if (value.length <= max) return value
        return value.take(max) + "...(len=${value.length})"
    }
}
