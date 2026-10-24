package io.github.magisk317.relay.android.common.utils

import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.smscode.runtime.common.utils.StringUtils
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.SecretRedactor

/**
 * Build-type-derived log sanitization. Debug/CI builds log raw (pure noop);
 * release builds redact credentials, summarize API payloads, and deep-sanitize
 * via [DefaultLogSanitizer].
 */
object SensitiveLogPolicy {
    private fun isPlaintextBuild(): Boolean =
        BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD

    @JvmStatic
    fun sanitizeLogMessage(message: String): String {
        if (isPlaintextBuild()) return message
        return DefaultLogSanitizer.sanitize(message)
    }

    @JvmStatic
    fun sanitizeSenderLogMessage(message: String): String {
        if (isPlaintextBuild()) return message
        var sanitized = SecretRedactor.redact(message)
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
}
