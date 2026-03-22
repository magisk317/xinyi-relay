package io.github.magisk317.relay.common.utils

import android.content.Context
import io.github.magisk317.relay.data.db.DBProvider
import io.github.magisk317.relay.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.data.store.EntityStoreManager
import io.github.magisk317.relay.data.store.EntityType
import io.github.magisk317.smscode.domain.model.AppLabelResolver
import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec

object SmsCodeUtils {
    private const val COLUMN_COMPANY = "company"
    private const val COLUMN_KEYWORD = "code_keyword"
    private const val COLUMN_REGEX = "code_regex"

    private suspend fun loadCodeKeywordsBySP(context: Context): String? = PrefsReader.getSMSCodeKeywords(context)

    suspend fun parseSmsCodeIfExists(
        context: Context,
        content: String,
        keywordsRegexOverride: String? = null,
    ): String {
        val keywordsRegex = keywordsRegexOverride ?: loadCodeKeywordsBySP(context).orEmpty()
        return io.github.magisk317.smscode.domain.utils.SmsCodeUtils.parseSmsCodeIfExists(
            content = content,
            keywordsRegex = keywordsRegex,
            rules = queryAllSmsCodeRules(context).map { it.toSpec() },
        )
    }

    @JvmStatic
    fun parseCompany(content: String): String =
        io.github.magisk317.smscode.domain.utils.SmsCodeUtils.parseCompany(content)

    @JvmStatic
    fun parseCompanyCandidates(content: String): List<String> =
        io.github.magisk317.smscode.domain.utils.SmsCodeUtils.parseCompanyCandidates(content)

    fun findPackageNameByLabel(context: Context, label: String?): String? {
        return io.github.magisk317.smscode.domain.utils.SmsCodeUtils.findPackageNameByLabel(
            label = label,
            resolver = AppLabelResolver { target ->
                resolvePackageNameByLabel(context, target)
            },
        )
    }

    private fun resolvePackageNameByLabel(context: Context, label: String): String? {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_ALL)
            for (app in apps) {
                if (pm.getApplicationLabel(app).toString().equals(label, ignoreCase = true)) {
                    return app.packageName
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryAllSmsCodeRules(context: Context): List<SmsCodeRule> {
        var rules: List<SmsCodeRule>
        try {
            val smsCodeRuleUri = DBProvider.smsCodeRuleContentUri(context)
            val resolver = context.contentResolver
            val projection = arrayOf(COLUMN_COMPANY, COLUMN_KEYWORD, COLUMN_REGEX)
            val cursor = resolver.query(smsCodeRuleUri, projection, null, null, null)
            if (cursor != null) {
                val resultRules = mutableListOf<SmsCodeRule>()
                while (cursor.moveToNext()) {
                    resultRules.add(
                        SmsCodeRule(
                            company = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COMPANY)),
                            codeKeyword = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_KEYWORD)),
                            codeRegex = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REGEX)),
                        ),
                    )
                }
                cursor.close()
                rules = if (resultRules.isNotEmpty()) {
                    XLog.d("Load SmsCode rules succeed by content provider")
                    resultRules
                } else {
                    EntityStoreManager.loadEntitiesFromFile(
                        context,
                        EntityType.CODE_RULES,
                        SmsCodeRule::class.java,
                    ).also {
                        XLog.w("Load SmsCode rules by file: provider returned empty result")
                    }
                }
            } else {
                throw IllegalStateException("Cursor is null for URI: $smsCodeRuleUri")
            }
        } catch (_: Throwable) {
            rules = EntityStoreManager.loadEntitiesFromFile(
                context,
                EntityType.CODE_RULES,
                SmsCodeRule::class.java,
            )
            XLog.d("Load SmsCode rules by file")
        }
        return rules
    }

    private fun SmsCodeRule.toSpec(): SmsCodeRuleSpec =
        SmsCodeRuleSpec(
            company = company,
            codeKeyword = codeKeyword,
            codeRegex = codeRegex,
        )
}
