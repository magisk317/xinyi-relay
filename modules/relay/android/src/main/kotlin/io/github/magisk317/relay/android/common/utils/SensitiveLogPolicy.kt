package io.github.magisk317.relay.android.common.utils

import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import io.github.magisk317.xposed.logging.SecretRedactor

/**
 * Sanitization follows the process's [MagiskLogPolicy]: internal diagnostic builds keep raw
 * text within the shared line budget, while public releases emit no logs. Redaction remains
 * defense in depth for callers that render text before the release gate.
 */
object SensitiveLogPolicy {
    private fun isPlaintextBuild(): Boolean = !MagiskLogPolicy.current().sanitizesText

    @JvmStatic
    fun sanitizeLogMessage(message: String): String {
        if (isPlaintextBuild()) return DefaultLogSanitizer.limitForLog(message)
        return DefaultLogSanitizer.sanitize(message)
    }

    @JvmStatic
    fun sanitizeSenderLogMessage(message: String): String {
        if (isPlaintextBuild()) return DefaultLogSanitizer.limitForLog(message)
        return DefaultLogSanitizer.sanitize(SecretRedactor.redact(message))
    }
}
