package io.github.magisk317.relay.xp.notification

import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.rule.utils.SmsCodeUtils
import java.util.Locale

/**
 * Host adapter over the smscode rule engine for notification-sourced content.
 *
 * Decides whether a capture came from a telephony SMS package, extracts the
 * verification code with the notification keyword vocabulary and resolves the
 * company label shown to the user.
 */
object SmsCodeNotificationAnalyzer {

    fun isTelephonySmsPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase(Locale.ROOT)
        if (normalized in TELEPHONY_SMS_PACKAGE_ALLOWLIST) return true
        return normalized.contains("telephony")
    }

    fun extractCode(
        content: String,
        source: SmsCodeParseSource?,
    ): String {
        if (content.isBlank()) return ""
        return SmsCodeUtils.parseSmsCodeResult(
            content = content,
            keywordsRegex = NOTIFICATION_KEYWORDS_REGEX,
            rules = NOTIFICATION_CODE_RULES,
            source = source,
        ).code
    }

    fun resolveCompanyLabel(
        title: String,
        body: String,
        expandedText: String,
        tickerText: String,
        fallbackLabel: String,
    ): String {
        val content = io.github.magisk317.xposed.capture.NotificationContentResolver.combinedContent(
            title = title,
            body = body,
            tickerText = tickerText,
            expandedText = expandedText,
        )
        val company = extractCompanyCandidates(content).firstOrNull().orEmpty().trim()
        if (company.isNotBlank()) return company
        return title.trim().takeIf { it.isNotBlank() } ?: fallbackLabel
    }

    private fun extractCompanyCandidates(content: String): List<String> {
        return COMPANY_PATTERN.findAll(content)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private val TELEPHONY_SMS_PACKAGE_ALLOWLIST = setOf(
        "com.android.phone",
        "com.android.providers.telephony",
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.textra",
    )
    private const val NOTIFICATION_KEYWORDS_REGEX =
        "验证码|校验码|检验码|确认码|激活码|动态码|安全码|(?i)(?<![a-z])(?:verification|verify|otp|passcode|pin|code)(?![a-z])"
    private const val NOTIFICATION_ALPHANUMERIC_CODE_REGEX =
        "(?i)(?<![A-Z0-9])(?=[A-Z0-9]{4,10}(?![A-Z0-9]))(?=[A-Z0-9]*\\d)[A-Z0-9]{4,10}"
    private val NOTIFICATION_CODE_RULES = listOf(
        SmsCodeRuleSpec(company = "Gmail", codeKeyword = "code", codeRegex = NOTIFICATION_ALPHANUMERIC_CODE_REGEX, packageNameHint = "com.google.android.gm"),
        SmsCodeRuleSpec(company = "Email", codeKeyword = "code", codeRegex = NOTIFICATION_ALPHANUMERIC_CODE_REGEX, packageNameHint = "com.android.email"),
        SmsCodeRuleSpec(
            company = "Google Voice",
            codeKeyword = "code",
            codeRegex = NOTIFICATION_ALPHANUMERIC_CODE_REGEX,
            packageNameHint = "com.google.android.apps.googlevoice",
            senderRegex = "Google Voice|Voice",
        ),
    )
    private val COMPANY_PATTERN = Regex("(?<=【)[^】]+(?=】)|(?<=\\[)[^]]+(?=])")
}
