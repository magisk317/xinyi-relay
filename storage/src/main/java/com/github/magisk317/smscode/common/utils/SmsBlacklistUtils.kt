package com.github.magisk317.smscode.common.utils

import android.content.Context

object SmsBlacklistUtils {

    data class MatchResult(
        val matched: Boolean,
        val matchType: String? = null,
        val pattern: String? = null,
        val actionDelete: Boolean = false,
        val actionBlock: Boolean = false,
    )

    @JvmStatic
    fun match(context: Context, sender: String?, body: String?): MatchResult {
        if (!PrefsReader.smsBlacklistEnabled(context)) {
            return MatchResult(matched = false)
        }
        val senderValue = sender.orEmpty()
        val bodyValue = body.orEmpty()
        if (senderValue.isBlank() && bodyValue.isBlank()) {
            return MatchResult(matched = false)
        }

        val delete = PrefsReader.smsBlacklistActionDelete(context)
        val block = PrefsReader.smsBlacklistActionBlock(context)

        fun matched(type: String, pattern: String): MatchResult =
            MatchResult(
                matched = true,
                matchType = type,
                pattern = pattern,
                actionDelete = delete,
                actionBlock = block,
            )

        val senderDigits = normalizeDigits(senderValue)

        val numberRules = splitRules(PrefsReader.smsBlacklistNumbers(context))
        numberRules.firstOrNull { rule ->
            val ruleDigits = normalizeDigits(rule)
            ruleDigits.isNotBlank() && senderDigits == ruleDigits
        }?.let { return matched("number", it) }

        val prefixRules = splitRules(PrefsReader.smsBlacklistPrefixes(context))
        prefixRules.firstOrNull { rule ->
            val ruleDigits = normalizeDigits(rule)
            ruleDigits.isNotBlank() && senderDigits.startsWith(ruleDigits)
        }?.let { return matched("prefix", it) }

        val contentRules = splitRules(PrefsReader.smsBlacklistContent(context))
        contentRules.firstOrNull { rule ->
            bodyValue.contains(rule, ignoreCase = true)
        }?.let { return matched("content", it) }

        val regexRules = splitRules(PrefsReader.smsBlacklistRegex(context))
        val target = buildString {
            append(senderValue)
            append('\n')
            append(bodyValue)
        }
        regexRules.firstOrNull { rule ->
            runCatching { Regex(rule, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(target) }.getOrDefault(false)
        }?.let { return matched("regex", it) }

        return MatchResult(matched = false)
    }

    private fun splitRules(raw: String): List<String> =
        raw.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun normalizeDigits(input: String): String = input.filter { it.isDigit() }
}

