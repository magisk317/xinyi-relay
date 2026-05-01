package io.github.magisk317.relay.data.repository

import android.content.Context
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.dao.AppInfoDao
import io.github.magisk317.relay.android.data.db.dao.ForwardFilterRuleDao
import io.github.magisk317.relay.android.data.db.dao.NotifyRouteRuleDao
import io.github.magisk317.relay.android.data.db.dao.SmsCodeRuleDao
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.relay.data.mapper.ConfigMapper.toEntity
import io.github.magisk317.relay.android.data.db.dao.RuleDao
import io.github.magisk317.relay.android.data.db.dao.SenderDao
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.model.Rule
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.android.platform.sender.SenderSettingSanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConfigRepository(
    context: Context,
    private val db: AppDatabase,
    private val smsCodeRuleDao: SmsCodeRuleDao,
    private val appInfoDao: AppInfoDao,
    private val notifyRouteRuleDao: NotifyRouteRuleDao,
    private val forwardFilterRuleDao: ForwardFilterRuleDao,
    private val ruleDao: RuleDao,
    private val senderDao: SenderDao,
) {
    private val appContext = context.applicationContext ?: context

    // Legacy SmsCodeRule
    fun observeSmsCodeRulesFlow(): Flow<List<SmsCodeRule>> = smsCodeRuleDao.getAllFlow()
    suspend fun getAllSmsCodeRules(): List<SmsCodeRule> = smsCodeRuleDao.getAll()
    suspend fun getSmsCodeRuleById(id: Long): SmsCodeRule? = smsCodeRuleDao.getById(id)
    suspend fun upsertSmsCodeRule(rule: SmsCodeRule): Long {
        val result = smsCodeRuleDao.insert(rule)
        noteMutation("config.sms_code_rule_upsert")
        return result
    }
    suspend fun deleteSmsCodeRule(rule: SmsCodeRule) {
        smsCodeRuleDao.delete(rule)
        noteMutation("config.sms_code_rule_delete")
    }
    suspend fun insertSmsCodeRules(rules: List<SmsCodeRule>) {
        smsCodeRuleDao.insertAll(rules)
        noteMutation("config.sms_code_rules_replace")
    }
    suspend fun clearAllSmsCodeRules() {
        smsCodeRuleDao.clearAll()
        noteMutation("config.sms_code_rules_clear")
    }

    // AppInfo
    fun getAllAppInfoFlow(): Flow<List<AppInfo>> = appInfoDao.getAllFlow()
    suspend fun getAllAppInfo(): List<AppInfo> = appInfoDao.getAll()
    suspend fun getAppInfoByPackage(packageName: String): AppInfo? = appInfoDao.getByPackageName(packageName)
    suspend fun upsertAppInfo(appInfo: AppInfo) {
        appInfoDao.insert(appInfo)
        noteMutation("config.app_info_upsert")
    }
    suspend fun removeAppInfosByPackage(packageNames: List<String>) {
        appInfoDao.deleteByPackageNames(packageNames)
        noteMutation("config.app_info_delete")
    }
    suspend fun clearAllAppInfo() {
        appInfoDao.clearAll()
        noteMutation("config.app_info_clear")
    }

    // NotifyRouteRule
    fun getAllNotifyRouteRulesFlow(): Flow<List<NotifyRouteRule>> = notifyRouteRuleDao.getAllFlow()
    fun observeNotifySenderIds(scope: Int, packageName: String): Flow<Set<Long>> =
        notifyRouteRuleDao.observeSenderIdsByScopeAndPackage(scope, packageName).mapToSet()
    fun observeNotifyPackageNames(scope: Int, senderId: Long): Flow<Set<String>> =
        notifyRouteRuleDao.observePackageNamesByScopeAndSender(scope, senderId).mapToSet()
    suspend fun deleteNotifyRouteByScopeAndPackage(scope: Int, packageName: String) {
        notifyRouteRuleDao.deleteByScopeAndPackage(scope, packageName)
        noteMutation("config.notify_route_delete_by_package")
    }
    suspend fun deleteNotifyRouteByScopesAndSender(scopes: List<Int>, senderId: Long) {
        notifyRouteRuleDao.deleteByScopesAndSender(scopes, senderId)
        noteMutation("config.notify_route_delete_by_sender")
    }
    suspend fun insertNotifyRouteRules(rules: List<NotifyRouteRule>) {
        notifyRouteRuleDao.insertAll(rules)
        noteMutation("config.notify_routes_replace")
    }
    suspend fun clearAllNotifyRouteRules() {
        notifyRouteRuleDao.clearAll()
        noteMutation("config.notify_routes_clear")
    }

    // ForwardFilterRule (New)
    fun getAllForwardFilterRulesFlow(): Flow<List<ForwardFilterRule>> = 
        forwardFilterRuleDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    
    fun observeForwardFiltersByScope(msgType: String, scopeType: String, scopeKey: String? = null, senderId: Long? = null): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.observeByScope(msgType, scopeType, scopeKey, senderId).map { list -> list.map { it.toDomain() } }
    
    fun observeForwardFiltersByScopePrefix(msgType: String, scopeType: String, scopeKeyPrefix: String): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.observeByScopePrefix(msgType, scopeType, scopeKeyPrefix).map { list -> list.map { it.toDomain() } }
    
    suspend fun insertForwardFilterRule(rule: ForwardFilterRule) {
        forwardFilterRuleDao.insert(rule.toEntity())
        noteMutation("config.forward_filter_insert")
    }
    suspend fun updateForwardFilterRule(rule: ForwardFilterRule) {
        forwardFilterRuleDao.update(rule.toEntity())
        noteMutation("config.forward_filter_update")
    }
    suspend fun deleteForwardFilterRuleById(id: Long) {
        forwardFilterRuleDao.deleteById(id)
        noteMutation("config.forward_filter_delete")
    }
    suspend fun updateForwardFilterEnabled(id: Long, enabled: Int, updateTime: Long) {
        forwardFilterRuleDao.updateEnabledById(id, enabled, updateTime)
        noteMutation("config.forward_filter_toggle")
    }
    suspend fun clearAllForwardFilterRules() {
        forwardFilterRuleDao.clearAll()
        noteMutation("config.forward_filter_clear")
    }

    // Rule (Legacy Forwarder Rule)
    fun getAllRulesFlow(): Flow<List<Rule>> = ruleDao.observeAll().map { list -> list.map { it.toDomain() } }
    fun observeRulesBySender(senderId: Long): Flow<List<Rule>> = ruleDao.observeBySender(senderId).map { list -> list.map { it.toDomain() } }
    suspend fun getAllRules(): List<Rule> = ruleDao.getAll().map { it.toDomain() }
    suspend fun getRuleById(id: Long): Rule = ruleDao.getOne(id).toDomain()
    suspend fun insertRule(rule: Rule) {
        ruleDao.insert(rule.toEntity())
        noteMutation("config.rule_insert")
    }
    suspend fun updateRule(rule: Rule) {
        ruleDao.update(rule.toEntity())
        noteMutation("config.rule_update")
    }
    suspend fun deleteRule(rule: Rule) {
        ruleDao.delete(rule.toEntity())
        noteMutation("config.rule_delete")
    }
    suspend fun clearAllRules() {
        ruleDao.deleteAll()
        noteMutation("config.rule_clear")
    }

    // Sender
    fun getAllSendersFlow(): Flow<List<Sender>> = senderDao.getAllFlow().map { list ->
        list.map { SenderSettingSanitizer.sanitizeSenderLenient(it.toDomain()) }
    }
    suspend fun getAllSenders(): List<Sender> = senderDao.getAll().map {
        SenderSettingSanitizer.sanitizeSenderLenient(it.toDomain())
    }
    suspend fun getSenderById(id: Long): Sender? = senderDao.getOne(id)?.toDomain()?.let {
        SenderSettingSanitizer.sanitizeSenderLenient(it)
    }
    suspend fun insertSender(sender: Sender): Long {
        val safeSender = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val id = senderDao.insert(safeSender.toEntity())
        noteMutation("config.sender_insert")
        return id
    }
    suspend fun updateSender(sender: Sender) {
        senderDao.update(SenderSettingSanitizer.sanitizeSenderLenient(sender).toEntity())
        noteMutation("config.sender_update")
    }
    suspend fun updateSenderStatus(ids: List<Long>, status: Int) {
        senderDao.updateStatusByIds(ids, status)
        noteMutation("config.sender_status")
    }
    suspend fun deleteSender(sender: Sender) {
        senderDao.delete(sender.toEntity())
        noteMutation("config.sender_delete")
    }
    suspend fun clearAllSenders() {
        senderDao.deleteAll()
        noteMutation("config.sender_clear")
    }

    /** 手动触发 WAL Checkpoint，确保 UI 进程写入的数据对 Hook 进程可见。 */
    suspend fun checkpoint() {
        try {
            db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .close()
        } catch (_: Throwable) {
        }
    }

    private fun <T> Flow<List<T>>.mapToSet(): Flow<Set<T>> = map { it.toSet() }

    private suspend fun noteMutation(source: String) {
        RuntimeGraph.from(appContext).remoteAgentRepository.noteLocalMutation(source)
    }
}
