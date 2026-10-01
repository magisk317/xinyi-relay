package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.engine.model.AppInfoData
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.model.NotifyRouteRuleData
import io.github.magisk317.relay.engine.model.Rule
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.model.SmsCodeRuleData
import kotlinx.coroutines.flow.Flow

interface AppConfigRepository {
    // Sender (domain type)
    fun getAllSendersFlow(): Flow<List<Sender>>
    suspend fun getAllSenders(): List<Sender>
    suspend fun getSenderById(id: Long): Sender?
    suspend fun insertSender(sender: Sender): Long
    suspend fun updateSender(sender: Sender)
    suspend fun updateSenderStatus(ids: List<Long>, status: Int)
    suspend fun updateSenderPriorities(priorityById: Map<Long, Int>)
    suspend fun deleteSender(sender: Sender)
    suspend fun clearAllSenders()

    // Rule (domain type)
    fun getAllRulesFlow(): Flow<List<Rule>>
    fun observeRulesBySender(senderId: Long): Flow<List<Rule>>
    suspend fun getAllRules(): List<Rule>
    suspend fun getRuleById(id: Long): Rule
    suspend fun insertRule(rule: Rule)
    suspend fun updateRule(rule: Rule)
    suspend fun deleteRule(rule: Rule)
    suspend fun clearAllRules()

    // ForwardFilterRule (domain type)
    fun getAllForwardFilterRulesFlow(): Flow<List<ForwardFilterRule>>
    fun observeForwardFiltersByScope(
        msgType: String,
        scopeType: String,
        scopeKey: String? = null,
        senderId: Long? = null,
    ): Flow<List<ForwardFilterRule>>

    fun observeForwardFiltersByScopePrefix(
        msgType: String,
        scopeType: String,
        scopeKeyPrefix: String,
    ): Flow<List<ForwardFilterRule>>

    suspend fun insertForwardFilterRule(rule: ForwardFilterRule)
    suspend fun updateForwardFilterRule(rule: ForwardFilterRule)
    suspend fun deleteForwardFilterRuleById(id: Long)
    suspend fun updateForwardFilterEnabled(id: Long, enabled: Int, updateTime: Long)
    suspend fun clearAllForwardFilterRules()

    // SmsCodeRule (abstract type)
    fun observeSmsCodeRulesFlow(): Flow<List<SmsCodeRuleData>>
    suspend fun getAllSmsCodeRules(): List<SmsCodeRuleData>
    suspend fun getSmsCodeRuleById(id: Long): SmsCodeRuleData?
    suspend fun upsertSmsCodeRule(rule: SmsCodeRuleData): Long
    suspend fun deleteSmsCodeRule(rule: SmsCodeRuleData)
    suspend fun insertSmsCodeRules(rules: List<SmsCodeRuleData>)
    suspend fun clearAllSmsCodeRules()

    // AppInfo (abstract type)
    fun getAllAppInfoFlow(): Flow<List<AppInfoData>>
    suspend fun getAllAppInfo(): List<AppInfoData>
    suspend fun getAppInfoByPackage(packageName: String): AppInfoData?
    suspend fun upsertAppInfo(appInfo: AppInfoData)
    suspend fun removeAppInfosByPackage(packageNames: List<String>)
    suspend fun clearAllAppInfo()

    // NotifyRouteRule (abstract type)
    fun getAllNotifyRouteRulesFlow(): Flow<List<NotifyRouteRuleData>>
    fun observeNotifySenderIds(scope: Int, packageName: String): Flow<Set<Long>>
    fun observeNotifyPackageNames(scope: Int, senderId: Long): Flow<Set<String>>
    suspend fun deleteNotifyRouteByScopeAndPackage(scope: Int, packageName: String)
    suspend fun deleteNotifyRouteByScopesAndSender(scopes: List<Int>, senderId: Long)
    suspend fun insertNotifyRouteRules(rules: List<NotifyRouteRuleData>)
    suspend fun clearAllNotifyRouteRules()

    // Utility
    suspend fun checkpoint()
}
