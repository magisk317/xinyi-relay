package io.github.magisk317.relay.android.sms

import android.content.Context
import android.database.SQLException
import io.github.magisk317.relay.android.data.db.DBProvider
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.android.data.store.EntityStoreManager
import io.github.magisk317.relay.android.data.store.EntityType
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.prefs.AppPreferencesDataStore
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.smscode.domain.model.SmsCodeParseResult
import io.github.magisk317.smscode.domain.model.SmsCodeParseSource
import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogRefreshResult
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogRepository
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogSnapshot
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleMerger
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleRemoteSource
import io.github.magisk317.smscode.runtime.common.sms.RuntimeSmsCodeAdapter
import io.github.magisk317.smscode.runtime.common.sms.SmsCodeRuleProvider
import io.github.magisk317.smscode.runtime.common.sms.SmsKeywordProvider
import io.github.magisk317.smscode.runtime.common.sms.SmsPackageLabelResolver
import kotlinx.coroutines.flow.Flow

object SmsCodeUtils {
    private const val COLUMN_COMPANY = "company"
    private const val COLUMN_KEYWORD = "code_keyword"
    private const val COLUMN_REGEX = "code_regex"

    private val adapter = RuntimeSmsCodeAdapter(
        keywordProvider = SmsKeywordProvider { context, override ->
            override ?: PrefsReader.getSMSCodeKeywords(context).orEmpty()
        },
        ruleProvider = SmsCodeRuleProvider { context ->
            loadMergedRuleSpecs(context)
        },
        labelResolver = SmsPackageLabelResolver { context, label ->
            resolvePackageNameByLabel(context, label)
        },
    )

    suspend fun parseSmsCodeIfExists(
        context: Context,
        content: String,
        keywordsRegexOverride: String? = null,
        source: SmsCodeParseSource? = null,
    ): String {
        return adapter.parseSmsCodeIfExists(context, content, keywordsRegexOverride, source)
    }

    suspend fun parseSmsCodeResultIfExists(
        context: Context,
        content: String,
        keywordsRegexOverride: String? = null,
        source: SmsCodeParseSource? = null,
    ): SmsCodeParseResult {
        return adapter.parseSmsCodeResultIfExists(context, content, keywordsRegexOverride, source)
    }

    @JvmStatic
    fun parseCompany(content: String): String = adapter.parseCompany(content)

    @JvmStatic
    fun parseCompanyCandidates(content: String): List<String> = adapter.parseCompanyCandidates(content)

    fun findPackageNameByLabel(context: Context, label: String?): String? {
        return adapter.findPackageNameByLabel(context, label)
    }

    suspend fun loadOfficialRuleSnapshot(context: Context): SmsCodeRuleCatalogSnapshot {
        return catalogRepository(context).loadOfficialRules().also(::logRejectedOfficialRules)
    }

    suspend fun refreshOfficialRules(context: Context): SmsCodeRuleCatalogRefreshResult {
        return catalogRepository(context).refreshOfficialRules().also { result ->
            result.snapshot?.let(::logRejectedOfficialRules)
            if (!result.success) {
                XLog.w("Refresh official SmsCode rules failed: %s", result.errorMessage ?: "unknown")
            }
        }
    }

    fun observeOfficialRuleSourceUrl(context: Context): Flow<String> =
        AppPreferencesDataStore.getStringFlow(
            context,
            PrefConst.KEY_SMS_CODE_RULE_SOURCE_URL,
            "",
        )

    suspend fun saveOfficialRuleSourceUrl(context: Context, value: String): String {
        val normalized = SmsCodeRuleRemoteSource.normalizeCustomBaseUrl(value).orEmpty()
        AppPreferencesDataStore.setString(
            context,
            PrefConst.KEY_SMS_CODE_RULE_SOURCE_URL,
            normalized,
        )
        return normalized
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

    private fun loadRulesFromFile(context: Context): List<SmsCodeRule> =
        EntityStoreManager.loadEntitiesFromFile(
            context,
            EntityType.CODE_RULES,
            SmsCodeRule::class.java,
        )

    private fun logProviderEmptyFallback(fileRules: List<SmsCodeRule>) {
        if (fileRules.isEmpty()) {
            XLog.d("Load SmsCode rules by file: provider empty and no persisted user rules")
        } else {
            XLog.w(
                "Load SmsCode rules by file: provider empty but %d persisted user rule(s) found",
                fileRules.size,
            )
        }
    }

    private fun logProviderFailureFallback(fileRules: List<SmsCodeRule>, throwable: Throwable) {
        if (fileRules.isEmpty()) {
            XLog.d(
                "Load SmsCode rules by file after provider failure: no persisted user rules, err=%s",
                throwable.message ?: throwable.javaClass.simpleName,
            )
        } else {
            XLog.w(
                "Load SmsCode rules by file after provider failure: %d persisted user rule(s) found, err=%s",
                fileRules.size,
                throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }

    private fun queryAllSmsCodeRules(context: Context): List<SmsCodeRule> {
        var rules: List<SmsCodeRule>
        try {
            val smsCodeRuleUri = DBProvider.smsCodeRuleContentUri(context)
            val resolver = context.contentResolver
            val projection = arrayOf(COLUMN_COMPANY, COLUMN_KEYWORD, COLUMN_REGEX)
            resolver.query(smsCodeRuleUri, projection, null, null, null)?.use { cursor ->
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
                rules = if (resultRules.isNotEmpty()) {
                    XLog.d("Load SmsCode rules succeed by content provider")
                    resultRules
                } else {
                    loadRulesFromFile(context).also(::logProviderEmptyFallback)
                }
                return rules
            }
            throw IllegalStateException("Cursor is null for URI: $smsCodeRuleUri")
        } catch (throwable: SecurityException) {
            rules = loadRulesFromFile(context)
            logProviderFailureFallback(rules, throwable)
        } catch (throwable: IllegalArgumentException) {
            rules = loadRulesFromFile(context)
            logProviderFailureFallback(rules, throwable)
        } catch (throwable: IllegalStateException) {
            rules = loadRulesFromFile(context)
            logProviderFailureFallback(rules, throwable)
        } catch (throwable: SQLException) {
            rules = loadRulesFromFile(context)
            logProviderFailureFallback(rules, throwable)
        }
        return rules
    }

    private fun SmsCodeRule.toSpec(): SmsCodeRuleSpec =
        SmsCodeRuleSpec(
            company = company,
            codeKeyword = codeKeyword,
            codeRegex = codeRegex,
        )

    private suspend fun loadMergedRuleSpecs(context: Context): List<SmsCodeRuleSpec> {
        val userRules = queryAllSmsCodeRules(context).map { it.toSpec() }
        val officialSnapshot = loadOfficialRuleSnapshot(context)
        return SmsCodeRuleMerger.merge(
            userRules = userRules,
            officialRules = officialSnapshot.rules,
        )
    }

    private suspend fun catalogRepository(context: Context): SmsCodeRuleCatalogRepository {
        val appContext = context.applicationContext ?: context
        val customBaseUrl = AppPreferencesDataStore.getString(
            appContext,
            PrefConst.KEY_SMS_CODE_RULE_SOURCE_URL,
            "",
        )
        return SmsCodeRuleCatalogRepository(
            context = appContext,
            remote = SmsCodeRuleRemoteSource(customBaseUrl = customBaseUrl),
            userAgent = "XinyiRelay/SmsCodeRules",
        )
    }

    private fun logRejectedOfficialRules(snapshot: SmsCodeRuleCatalogSnapshot) {
        if (snapshot.rejectedRules.isEmpty()) return
        XLog.w(
            "Official SmsCode rules rejected: source=%s count=%d first=%s",
            snapshot.sourceKind.name,
            snapshot.rejectedRules.size,
            snapshot.rejectedRules.first(),
        )
    }
}
