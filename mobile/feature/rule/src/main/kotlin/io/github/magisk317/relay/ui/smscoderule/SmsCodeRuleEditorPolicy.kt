package io.github.magisk317.relay.ui.smscoderule

import io.github.magisk317.forward.engine.model.AppCodeRuleData
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

internal data class SmsCodeRuleEditorInput(
    val company: String?,
    val keyword: String,
    val regex: String,
)

internal enum class SmsCodeRuleInputError {
    EMPTY_KEYWORD,
    EMPTY_REGEX,
    INVALID_REGEX,
}

internal data class SmsCodeRuleInputValidation(
    val input: SmsCodeRuleEditorInput? = null,
    val error: SmsCodeRuleInputError? = null,
    val errorMessage: String? = null,
)

internal fun validateSmsCodeRuleInput(
    company: String,
    keyword: String,
    regex: String,
): SmsCodeRuleInputValidation {
    val normalized = SmsCodeRuleEditorInput(company.trim().ifBlank { null }, keyword.trim(), regex.trim())
    if (normalized.keyword.isEmpty()) return SmsCodeRuleInputValidation(error = SmsCodeRuleInputError.EMPTY_KEYWORD)
    if (normalized.regex.isEmpty()) return SmsCodeRuleInputValidation(error = SmsCodeRuleInputError.EMPTY_REGEX)
    try {
        Pattern.compile(normalized.regex)
    } catch (error: PatternSyntaxException) {
        return SmsCodeRuleInputValidation(error = SmsCodeRuleInputError.INVALID_REGEX, errorMessage = error.message)
    }
    return SmsCodeRuleInputValidation(input = normalized)
}

internal fun isDuplicateSmsCodeRule(
    input: SmsCodeRuleEditorInput,
    ruleId: Long,
    existingRules: List<AppCodeRuleData>,
): Boolean = existingRules.any { existing ->
    existing.id != ruleId &&
        existing.company.orEmpty().trim() == input.company.orEmpty() &&
        existing.codeKeyword.trim() == input.keyword &&
        existing.codeRegex.trim() == input.regex
}
