package io.github.magisk317.relay.desktop.ui

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Formats backend RFC3339 timestamps for display, falling back to the raw
 * string when the value is missing or unparsable.
 */
fun formatTimestamp(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return ""
    val instant = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
        ?: return value
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return runCatching { formatter.format(instant) }.getOrDefault(value)
}

/** Locale-aware "never seen" fallback used where the React page prints an empty last-seen time. */
fun formatOptionalTimestamp(raw: String?): String = formatTimestamp(raw).ifEmpty { "-" }
