package io.github.magisk317.relay.feature.reminder

object KeywordAlertMatcher {
    fun parseKeywords(raw: String): List<String> {
        return raw
            .split('\n', '\r', ',', '，', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun firstMatchedKeyword(rawKeywords: String, text: String): String? {
        if (text.isBlank()) return null
        return parseKeywords(rawKeywords).firstOrNull { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
    }
}
