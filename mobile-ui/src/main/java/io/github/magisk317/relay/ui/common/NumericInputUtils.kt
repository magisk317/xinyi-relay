package io.github.magisk317.relay.ui.common

internal fun filterNonNegativeIntegerInput(raw: String): String = buildString(raw.length) {
    raw.forEach { ch ->
        ch.digitToIntOrNull()?.let { append(it) }
    }
}

internal fun normalizeIntegerInput(raw: String): String {
    val normalized = StringBuilder(raw.length)
    raw.forEach { ch ->
        when {
            ch.isWhitespace() || Character.getType(ch) == Character.FORMAT.toInt() -> Unit
            ch.digitToIntOrNull() != null -> normalized.append(ch.digitToInt())
            else -> normalized.append(ch)
        }
    }
    return normalized.toString()
}

internal fun parseNonNegativeLongInput(raw: String): Long? {
    return normalizeIntegerInput(raw)
        .toLongOrNull()
        ?.takeIf { it >= 0L }
}

internal fun parseIntAtLeastInput(raw: String, min: Int): Int? {
    return normalizeIntegerInput(raw)
        .toIntOrNull()
        ?.takeIf { it >= min }
}

internal fun parseIntInRangeInput(raw: String, range: IntRange): Int? {
    return normalizeIntegerInput(raw)
        .toIntOrNull()
        ?.takeIf { it in range }
}
