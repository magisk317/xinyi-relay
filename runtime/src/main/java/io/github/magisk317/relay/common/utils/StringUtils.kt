package io.github.magisk317.relay.common.utils

object StringUtils {

    @JvmStatic
    fun escape(str: String?): String? {
        if (str == null) return null

        val sb = StringBuilder(str.length + 2)
        sb.append('"')
        for (c in str) {
            when (c) {
                '\t' -> sb.append("\\t")

                '\b' -> sb.append("\\b")

                '\n' -> sb.append("\\n")

                '\r' -> sb.append("\\r")

                '\u000C' -> sb.append("\\f")

                // '\f' is not allowed in Kotlin character literal sometimes, using unicode
                '\\' -> sb.append("\\\\")

                '\'' -> sb.append("\\'")

                '\"' -> sb.append("\\\"")

                else -> {
                    if (c.code < 32 || c.code >= 127) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    @JvmStatic
    fun shortHash(str: String?): String {
        val value = str.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    @JvmStatic
    fun maskKeepEdges(str: String?, prefix: Int, suffix: Int): String {
        val value = str.orEmpty()
        if (value.isBlank()) return "<empty>"
        val safePrefix = prefix.coerceAtLeast(0)
        val safeSuffix = suffix.coerceAtLeast(0)
        if (value.length <= safePrefix + safeSuffix) {
            return "*".repeat(value.length.coerceAtLeast(1))
        }
        return buildString(value.length) {
            append(value.take(safePrefix))
            append("*".repeat((value.length - safePrefix - safeSuffix).coerceAtLeast(1)))
            append(value.takeLast(safeSuffix))
        }
    }

    @JvmStatic
    fun summarizeSender(str: String?): String {
        val value = str.orEmpty()
        if (value.isBlank()) return "sender[len=0 hash=none]"
        return "sender[len=${value.length} tail=${value.takeLast(minOf(2, value.length))} hash=${shortHash(value)}]"
    }

    @JvmStatic
    fun summarizeBody(str: String?): String {
        val value = str.orEmpty()
        if (value.isBlank()) return "body[len=0 hash=none]"
        return "body[len=${value.length} hash=${shortHash(value)}]"
    }

    @JvmStatic
    fun summarizeCode(str: String?): String {
        val value = str.orEmpty()
        if (value.isBlank()) return "code[len=0 hash=none]"
        return "code[len=${value.length} masked=${maskKeepEdges(value, 2, 1)} hash=${shortHash(value)}]"
    }

    @JvmStatic
    fun summarizePayload(str: String?): String {
        val value = str.orEmpty()
        if (value.isBlank()) return "payload[len=0 hash=none]"
        return "payload[len=${value.length} hash=${shortHash(value)}]"
    }
}
