package io.github.magisk317.relay.android.common.utils

import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.smscode.runtime.contract.logging.DefaultLogSanitizer
import io.github.magisk317.smscode.runtime.common.utils.StringUtils
import java.util.Locale

object SensitiveLogPolicy {
    private const val ENABLED_LOG_MAX_LENGTH = 1200
    private const val SANITIZED_LOG_MAX_LENGTH = 800

    private val messageFieldNames = listOf(
        "from",
        "sender",
        "content",
        "msg",
        "message",
        "text",
        "body",
        "title",
        "org_content",
        "phone",
        "mobile",
        "number",
        "code",
    )

    @Volatile
    private var enabled = false

    @JvmStatic
    fun isSupported(): Boolean = BuildConfig.DEBUG

    @JvmStatic
    fun isEnabled(): Boolean = isSupported() && enabled

    @JvmStatic
    fun setEnabled(value: Boolean) {
        enabled = isSupported() && value
    }

    @JvmStatic
    fun sanitizeLogMessage(message: String): String {
        val sanitized = maskSecrets(message)
        if (isEnabled()) {
            return truncate(sanitized, ENABLED_LOG_MAX_LENGTH)
        }
        return DefaultLogSanitizer.sanitize(sanitized)
    }

    @JvmStatic
    fun sanitizeSenderLogMessage(message: String): String {
        var sanitized = maskSecrets(message)
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
        sanitized = summarizeFieldValue(sanitized, "body")
        sanitized = summarizeFieldValue(sanitized, "text")
        sanitized = summarizeKnownMessageFields(sanitized)
        return truncate(DefaultLogSanitizer.sanitize(sanitized), SANITIZED_LOG_MAX_LENGTH)
    }

    private fun summarizeAfterLabel(message: String, label: String): String {
        val index = message.indexOf(label, ignoreCase = true)
        if (index < 0) return message
        val start = index + label.length
        return message.substring(0, start) + StringUtils.summarizePayload(message.substring(start).trim())
    }

    private fun summarizeFieldValue(message: String, fieldName: String): String {
        val regex = Regex("(?i)(\\b$fieldName=)([^\\s]+)")
        return regex.replace(message) { match ->
            match.groupValues[1] + StringUtils.summarizePayload(match.groupValues[2])
        }
    }

    private fun summarizeKnownMessageFields(message: String): String {
        var text = message
        messageFieldNames.forEach { field ->
            val queryRegex = Regex("(?i)(\\b$field=)([^&\\s]+)")
            text = queryRegex.replace(text) { match ->
                match.groupValues[1] + StringUtils.summarizePayload(match.groupValues[2])
            }
            val jsonRegex = Regex("(?i)(\"$field\"\\s*:\\s*\")([^\"]*)(\")")
            text = jsonRegex.replace(text) { match ->
                match.groupValues[1] + StringUtils.summarizePayload(match.groupValues[2]) + match.groupValues[3]
            }
        }
        return text
    }

    private fun maskSecrets(raw: String): String {
        var text = raw
        val urlUserInfo = Regex("(https?://)([^/@\\s:]+):([^@\\s]+)@")
        text = urlUserInfo.replace(text) { match ->
            "${match.groupValues[1]}***:***@"
        }
        val keyValuePattern = Regex(
            "(?i)(access_token|token|secret|sign|authorization|password|passwd|pwd|proxy-authorization)=([^&\\s,\\\"]+)",
        )
        text = keyValuePattern.replace(text) { match ->
            "${match.groupValues[1]}=***"
        }
        val bearerPattern = Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-+/=]+")
        text = bearerPattern.replace(text) { match ->
            "${match.groupValues[1]}***"
        }
        val basicPattern = Regex("(?i)(basic\\s+)[A-Za-z0-9+/=]+")
        text = basicPattern.replace(text) { match ->
            "${match.groupValues[1]}***"
        }
        return text
    }

    private fun truncate(value: String, max: Int): String {
        if (value.length <= max) return value
        return value.take(max) + "...(len=${value.length})"
    }
}
