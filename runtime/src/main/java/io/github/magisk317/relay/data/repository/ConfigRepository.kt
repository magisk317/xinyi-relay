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
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toEntity
import io.github.magisk317.relay.android.data.db.dao.RuleDao
import io.github.magisk317.relay.android.data.db.dao.SenderDao
import io.github.magisk317.relay.engine.model.AppInfoData
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.model.NotifyRouteRuleData
import io.github.magisk317.relay.engine.model.Rule
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.model.SmsCodeRuleData
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.sender.SenderSettingSanitizer
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
) : AppConfigRepository {
    private val appContext = context.applicationContext ?: context

    // Legacy SmsCodeRule
    override fun observeSmsCodeRulesFlow(): Flow<List<SmsCodeRuleData>> = smsCodeRuleDao.getAllFlow()
    override suspend fun getAllSmsCodeRules(): List<SmsCodeRuleData> = smsCodeRuleDao.getAll()
    override suspend fun getSmsCodeRuleById(id: Long): SmsCodeRuleData? = smsCodeRuleDao.getById(id)
    override suspend fun upsertSmsCodeRule(rule: SmsCodeRuleData): Long {
        val result = smsCodeRuleDao.insert(rule as SmsCodeRule)
        noteMutation("config.sms_code_rule_upsert")
        return result
    }
    override suspend fun deleteSmsCodeRule(rule: SmsCodeRuleData) {
        smsCodeRuleDao.delete(rule as SmsCodeRule)
        noteMutation("config.sms_code_rule_delete")
    }
    override suspend fun insertSmsCodeRules(rules: List<SmsCodeRuleData>) {
        smsCodeRuleDao.insertAll(rules.map { it as SmsCodeRule })
        noteMutation("config.sms_code_rules_replace")
    }
    override suspend fun clearAllSmsCodeRules() {
        smsCodeRuleDao.clearAll()
        noteMutation("config.sms_code_rules_clear")
    }

    // AppInfo
    override fun getAllAppInfoFlow(): Flow<List<AppInfoData>> = appInfoDao.getAllFlow()
    override suspend fun getAllAppInfo(): List<AppInfoData> = appInfoDao.getAll()
    override suspend fun getAppInfoByPackage(packageName: String): AppInfoData? = appInfoDao.getByPackageName(packageName)
    override suspend fun upsertAppInfo(appInfo: AppInfoData) {
        appInfoDao.insert(appInfo as AppInfo)
        noteMutation("config.app_info_upsert")
    }
    override suspend fun removeAppInfosByPackage(packageNames: List<String>) {
        appInfoDao.deleteByPackageNames(packageNames)
        noteMutation("config.app_info_delete")
    }
    override suspend fun clearAllAppInfo() {
        appInfoDao.clearAll()
        noteMutation("config.app_info_clear")
    }

    // NotifyRouteRule
    override fun getAllNotifyRouteRulesFlow(): Flow<List<NotifyRouteRuleData>> = notifyRouteRuleDao.getAllFlow()
    override fun observeNotifySenderIds(scope: Int, packageName: String): Flow<Set<Long>> =
        notifyRouteRuleDao.observeSenderIdsByScopeAndPackage(scope, packageName).mapToSet()
    override fun observeNotifyPackageNames(scope: Int, senderId: Long): Flow<Set<String>> =
        notifyRouteRuleDao.observePackageNamesByScopeAndSender(scope, senderId).mapToSet()
    override suspend fun deleteNotifyRouteByScopeAndPackage(scope: Int, packageName: String) {
        notifyRouteRuleDao.deleteByScopeAndPackage(scope, packageName)
        noteMutation("config.notify_route_delete_by_package")
    }
    override suspend fun deleteNotifyRouteByScopesAndSender(scopes: List<Int>, senderId: Long) {
        notifyRouteRuleDao.deleteByScopesAndSender(scopes, senderId)
        noteMutation("config.notify_route_delete_by_sender")
    }
    override suspend fun insertNotifyRouteRules(rules: List<NotifyRouteRuleData>) {
        notifyRouteRuleDao.insertAll(rules.map { it as NotifyRouteRule })
        noteMutation("config.notify_routes_replace")
    }
    override suspend fun clearAllNotifyRouteRules() {
        notifyRouteRuleDao.clearAll()
        noteMutation("config.notify_routes_clear")
    }

    // ForwardFilterRule (New)
    override fun getAllForwardFilterRulesFlow(): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    override fun observeForwardFiltersByScope(msgType: String, scopeType: String, scopeKey: String?, senderId: Long?): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.observeByScope(msgType, scopeType, scopeKey, senderId).map { list -> list.map { it.toDomain() } }

    override fun observeForwardFiltersByScopePrefix(msgType: String, scopeType: String, scopeKeyPrefix: String): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.observeByScopePrefix(msgType, scopeType, scopeKeyPrefix).map { list -> list.map { it.toDomain() } }

    override suspend fun insertForwardFilterRule(rule: ForwardFilterRule) {
        forwardFilterRuleDao.insert(rule.toEntity())
        noteMutation("config.forward_filter_insert")
    }
    override suspend fun updateForwardFilterRule(rule: ForwardFilterRule) {
        forwardFilterRuleDao.update(rule.toEntity())
        noteMutation("config.forward_filter_update")
    }
    override suspend fun deleteForwardFilterRuleById(id: Long) {
        forwardFilterRuleDao.deleteById(id)
        noteMutation("config.forward_filter_delete")
    }
    override suspend fun updateForwardFilterEnabled(id: Long, enabled: Int, updateTime: Long) {
        forwardFilterRuleDao.updateEnabledById(id, enabled, updateTime)
        noteMutation("config.forward_filter_toggle")
    }
    override suspend fun clearAllForwardFilterRules() {
        forwardFilterRuleDao.clearAll()
        noteMutation("config.forward_filter_clear")
    }

    // Rule (Legacy Forwarder Rule)
    override fun getAllRulesFlow(): Flow<List<Rule>> = ruleDao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeRulesBySender(senderId: Long): Flow<List<Rule>> = ruleDao.observeBySender(senderId).map { list -> list.map { it.toDomain() } }
    override suspend fun getAllRules(): List<Rule> = ruleDao.getAll().map { it.toDomain() }
    override suspend fun getRuleById(id: Long): Rule = ruleDao.getOne(id).toDomain()
    override suspend fun insertRule(rule: Rule) {
        ruleDao.insert(rule.toEntity())
        noteMutation("config.rule_insert")
    }
    override suspend fun updateRule(rule: Rule) {
        ruleDao.update(rule.toEntity())
        noteMutation("config.rule_update")
    }
    override suspend fun deleteRule(rule: Rule) {
        ruleDao.delete(rule.toEntity())
        noteMutation("config.rule_delete")
    }
    override suspend fun clearAllRules() {
        ruleDao.deleteAll()
        noteMutation("config.rule_clear")
    }

    // Sender
    override fun getAllSendersFlow(): Flow<List<Sender>> = senderDao.getAllFlow().map { list ->
        list.map { SenderSettingSanitizer.sanitizeSenderLenient(it.toDomain()) }
    }
    override suspend fun getAllSenders(): List<Sender> = senderDao.getAll().map {
        SenderSettingSanitizer.sanitizeSenderLenient(it.toDomain())
    }
    override suspend fun getSenderById(id: Long): Sender? = senderDao.getOne(id)?.toDomain()?.let {
        SenderSettingSanitizer.sanitizeSenderLenient(it)
    }
    override suspend fun insertSender(sender: Sender): Long {
        val safeSender = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val id = senderDao.insert(safeSender.toEntity())
        noteMutation("config.sender_insert")
        return id
    }
    override suspend fun updateSender(sender: Sender) {
        senderDao.update(SenderSettingSanitizer.sanitizeSenderLenient(sender).toEntity())
        noteMutation("config.sender_update")
    }
    override suspend fun updateSenderStatus(ids: List<Long>, status: Int) {
        senderDao.updateStatusByIds(ids, status)
        noteMutation("config.sender_status")
    }
    override suspend fun updateSenderPriorities(priorityById: Map<Long, Int>) {
        priorityById.forEach { (id, priority) ->
            senderDao.updatePriorityById(id, priority)
        }
        noteMutation("config.sender_priority")
    }
    override suspend fun deleteSender(sender: Sender) {
        senderDao.delete(sender.toEntity())
        noteMutation("config.sender_delete")
    }
    override suspend fun clearAllSenders() {
        senderDao.deleteAll()
        noteMutation("config.sender_clear")
    }

    /** 手动触发 WAL Checkpoint，确保 UI 进程写入的数据对 Hook 进程可见。 */
    override suspend fun checkpoint() {
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
