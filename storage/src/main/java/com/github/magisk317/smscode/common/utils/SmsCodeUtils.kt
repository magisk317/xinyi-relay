package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.text.TextUtils
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.data.db.entity.SmsCodeRule
import com.github.magisk317.smscode.feature.store.EntityStoreManager
import com.github.magisk317.smscode.feature.store.EntityType
import java.util.Locale
import java.util.regex.Pattern

/**
 * 验证码相关Utils
 */
object SmsCodeUtils {
    private const val COLUMN_COMPANY = "company"
    private const val COLUMN_KEYWORD = "code_keyword"
    private const val COLUMN_REGEX = "code_regex"

    private const val LEVEL_DIGITAL_6 = 4
    private const val LEVEL_DIGITAL_4 = 3
    private const val LEVEL_DIGITAL_OTHERS = 2
    private const val LEVEL_TEXT = 1
    private const val LEVEL_CHARACTER = 0
    private const val LEVEL_NONE = -1
    private val URL_SCHEME_TOKENS = setOf("http", "https", "www")

    /**
     * 是否包含中文
     */
    private fun containsChinese(text: String): Boolean {
        val regex = "[\u4e00-\u9fa5]|。"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(text)
        return matcher.find()
    }

    /**
     * 解析文本内容中的验证码关键字，如果有则返回第一个匹配到的关键字，否则返回 空字符串
     */
    private fun parseKeyword(keywordsRegex: String, content: String): String {
        val pattern = Pattern.compile(keywordsRegex)
        val matcher = pattern.matcher(content)
        return if (matcher.find()) {
            matcher.group()
        } else {
            ""
        }
    }

    // Hook side should avoid direct DataStore reads because createPackageContext/applicationContext can be unstable.
    private suspend fun loadCodeKeywordsBySP(context: Context): String? = PrefsReader.getSMSCodeKeywords(context)

    /**
     * 解析文本中的验证码并返回，如果不存在返回空字符
     */
    suspend fun parseSmsCodeIfExists(context: Context, content: String): String {
        val customResult = parseByCustomRules(context, content)
        val defaultResult = parseByDefaultRule(context, content)
        return pickBetterCode(customResult, defaultResult)
    }

    /**
     * Parse SMS code by default rule
     */
    private suspend fun parseByDefaultRule(context: Context, content: String): String {
        var result = ""
        val keywordsRegex = loadCodeKeywordsBySP(context) ?: ""
        val keyword = parseKeyword(keywordsRegex, content)
        if (!TextUtils.isEmpty(keyword)) {
            val cnCode = if (containsChinese(content)) getSmsCodeCN(keyword, content) else ""
            val enCode = getSmsCodeEN(keyword, content)
            result = pickBetterCode(cnCode, enCode)
        }
        return result
    }

    /**
     * 获取中文短信中包含的验证码
     */
    private fun getSmsCodeCN(keyword: String, content: String): String {
        val codeRegex = "(?<![a-zA-Z0-9])[a-zA-Z0-9]{4,8}(?![a-zA-Z0-9])"
        val handledContent = removeAllWhiteSpaces(content)
        var smsCode = getSmsCode(codeRegex, keyword, handledContent)
        if (TextUtils.isEmpty(smsCode)) {
            smsCode = getSmsCode(codeRegex, keyword, content)
        }
        return smsCode
    }

    /**
     * 获取英文短信包含的验证码
     */
    private fun getSmsCodeEN(keyword: String, content: String): String {
        val codeRegex = "(?<![0-9])[0-9]{4,8}(?![0-9])"
        var smsCode = getSmsCode(codeRegex, keyword, content)
        if (TextUtils.isEmpty(smsCode)) {
            val handledContent = removeAllWhiteSpaces(content)
            smsCode = getSmsCode(codeRegex, keyword, handledContent)
        }
        return smsCode
    }

    /**
     * Remove all white spaces.
     */
    private fun removeAllWhiteSpaces(content: String): String = content.replace("\\s*".toRegex(), "")

    /**
     * Parse SMS code
     */
    private fun getSmsCode(codeRegex: String, keyword: String, content: String): String {
        val p = Pattern.compile(codeRegex)
        val m = p.matcher(content)
        val possibleCodes = mutableListOf<String>()
        while (m.find()) {
            val candidate = m.group()
            if (candidate.lowercase(Locale.ROOT) in URL_SCHEME_TOKENS) continue
            possibleCodes.add(candidate)
        }
        if (possibleCodes.isEmpty()) return ""

        var filteredCodes = possibleCodes.filter { isNearToKeyword(keyword, it, content) }
        if (filteredCodes.isEmpty()) {
            filteredCodes = possibleCodes
        }

        var maxMatchLevel = LEVEL_NONE
        var minDistance = content.length
        var smsCode = ""
        for (filteredCode in filteredCodes) {
            val curLevel = getMatchLevel(filteredCode)
            if (curLevel > maxMatchLevel) {
                maxMatchLevel = curLevel
                minDistance = distanceToKeyword(keyword, filteredCode, content)
                smsCode = filteredCode
            } else if (curLevel == maxMatchLevel) {
                val curDistance = distanceToKeyword(keyword, filteredCode, content)
                if (curDistance < minDistance) {
                    minDistance = curDistance
                    smsCode = filteredCode
                }
            }
        }
        return smsCode
    }

    private fun getMatchLevel(matchedStr: String): Int = when {
        matchedStr.matches("^[0-9]{6}$".toRegex()) -> LEVEL_DIGITAL_6
        matchedStr.matches("^[0-9]{4}$".toRegex()) -> LEVEL_DIGITAL_4
        matchedStr.matches("^[0-9]*$".toRegex()) -> LEVEL_DIGITAL_OTHERS
        matchedStr.matches("^[a-zA-Z]*$".toRegex()) -> LEVEL_CHARACTER
        else -> LEVEL_TEXT
    }

    private fun pickBetterCode(first: String, second: String): String {
        if (first.isEmpty()) return second
        if (second.isEmpty()) return first
        val firstLevel = getMatchLevel(first)
        val secondLevel = getMatchLevel(second)
        return if (secondLevel > firstLevel) second else first
    }

    private fun isNearToKeyword(keyword: String, possibleCode: String, content: String): Boolean =
        distanceToKeyword(keyword, possibleCode, content) <= KEYWORD_DISTANCE_THRESHOLD

    private fun distanceToKeyword(keyword: String, possibleCode: String, content: String): Int {
        val possibleCodeIdx = content.indexOf(possibleCode)
        if (possibleCodeIdx < 0) return content.length

        var minDistance = content.length
        var searchStart = 0
        while (searchStart < content.length) {
            val keywordIdx = content.indexOf(keyword, startIndex = searchStart)
            if (keywordIdx < 0) break
            val distance = Math.abs(keywordIdx - possibleCodeIdx)
            if (distance < minDistance) {
                minDistance = distance
            }
            searchStart = keywordIdx + keyword.length
        }
        return minDistance
    }

    private suspend fun parseByCustomRules(context: Context, content: String): String {
        val rules = queryAllSmsCodeRules(context)
        val lowerContent = content.lowercase()
        for (rule in rules) {
            if (lowerContent.contains(rule.company?.lowercase() ?: "") &&
                lowerContent.contains(rule.codeKeyword.lowercase())
            ) {
                val pattern = Pattern.compile(rule.codeRegex)
                val matcher = pattern.matcher(content)
                if (matcher.find()) {
                    return matcher.group()
                }
            }
        }
        return ""
    }

    private fun queryAllSmsCodeRules(context: Context): List<SmsCodeRule> {
        var rules: List<SmsCodeRule>
        try {
            val smsCodeRuleUri = DBProvider.SMS_CODE_RULE_URI
            val resolver = context.contentResolver

            val companyColumn = COLUMN_COMPANY
            val keywordColumn = COLUMN_KEYWORD
            val regexColumn = COLUMN_REGEX

            val projection = arrayOf(companyColumn, keywordColumn, regexColumn)

            val cursor = resolver.query(smsCodeRuleUri, projection, null, null, null)
            if (cursor != null) {
                val resultRules = mutableListOf<SmsCodeRule>()
                while (cursor.moveToNext()) {
                    val rule = SmsCodeRule(
                        company = cursor.getString(cursor.getColumnIndexOrThrow(companyColumn)),
                        codeKeyword = cursor.getString(cursor.getColumnIndexOrThrow(keywordColumn)),
                        codeRegex = cursor.getString(cursor.getColumnIndexOrThrow(regexColumn)),
                    )
                    resultRules.add(rule)
                }
                cursor.close()
                if (resultRules.isNotEmpty()) {
                    XLog.d("Load SmsCode rules succeed by content provider")
                    rules = resultRules
                } else {
                    // Provider is reachable but may return empty during cross-process cold state.
                    // Fallback to file to keep behavior stable for hook side.
                    rules = EntityStoreManager.loadEntitiesFromFile(
                        context,
                        EntityType.CODE_RULES,
                        SmsCodeRule::class.java,
                    )
                    XLog.w("Load SmsCode rules by file: provider returned empty result")
                }
            } else {
                throw IllegalStateException("Cursor is null for URI: $smsCodeRuleUri")
            }
        } catch (ignored: Throwable) {
            rules = EntityStoreManager.loadEntitiesFromFile(
                context,
                EntityType.CODE_RULES,
                SmsCodeRule::class.java,
            )
            XLog.d("Load SmsCode rules by file")
        }
        return rules
    }

    @JvmStatic
    fun parseCompany(content: String): String {
        val possibleCompanies = parseCompanyCandidates(content)
        if (possibleCompanies.isEmpty()) return ""
        val sb = StringBuilder()
        var needSpace = false
        for (company in possibleCompanies) {
            if (needSpace) {
                sb.append(' ')
            } else {
                needSpace = true
            }
            sb.append(company)
        }
        return sb.toString()
    }

    @JvmStatic
    fun parseCompanyCandidates(content: String): List<String> {
        val regex = "((?<=【)(.*?)(?=】))|((?<=\\[)(.*?)(?=\\]))"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(content)
        val possibleCompanies = mutableListOf<String>()
        while (matcher.find()) {
            possibleCompanies.add(matcher.group())
        }
        return possibleCompanies
    }

    fun findPackageNameByLabel(context: Context, label: String?): String? {
        if (label.isNullOrBlank()) return null
        try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_ALL)
            for (app in apps) {
                if (pm.getApplicationLabel(app).toString().equals(label, ignoreCase = true)) {
                    return app.packageName
                }
            }
        } catch (ignored: Exception) {
            // ignore
        }
        return null
    }
    private const val KEYWORD_DISTANCE_THRESHOLD = 30
}
