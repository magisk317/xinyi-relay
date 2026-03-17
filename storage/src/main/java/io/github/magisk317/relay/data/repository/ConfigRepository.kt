package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.dao.AppInfoDao
import io.github.magisk317.relay.data.db.dao.ForwardFilterRuleDao
import io.github.magisk317.relay.data.db.dao.NotifyRouteRuleDao
import io.github.magisk317.relay.data.db.dao.SmsCodeRuleDao
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.relay.data.mapper.ConfigMapper.toEntity
import io.github.magisk317.relay.data.db.dao.RuleDao
import io.github.magisk317.relay.data.db.dao.SenderDao
import io.github.magisk317.relay.model.ForwardFilterRule
import io.github.magisk317.relay.model.Rule
import io.github.magisk317.relay.model.Sender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConfigRepository(
    private val db: AppDatabase,
    private val smsCodeRuleDao: SmsCodeRuleDao,
    private val appInfoDao: AppInfoDao,
    private val notifyRouteRuleDao: NotifyRouteRuleDao,
    private val forwardFilterRuleDao: ForwardFilterRuleDao,
    private val ruleDao: RuleDao,
    private val senderDao: SenderDao,
) {
    // Legacy SmsCodeRule
    suspend fun getAllSmsCodeRules(): List<SmsCodeRule> = smsCodeRuleDao.getAll()
    suspend fun insertSmsCodeRules(rules: List<SmsCodeRule>) = smsCodeRuleDao.insertAll(rules)
    suspend fun clearAllSmsCodeRules() = smsCodeRuleDao.clearAll()

    // AppInfo
    fun getAllAppInfoFlow(): Flow<List<AppInfo>> = appInfoDao.getAllFlow()
    suspend fun getAllAppInfo(): List<AppInfo> = appInfoDao.getAll()
    suspend fun getAppInfoByPackage(packageName: String): AppInfo? = appInfoDao.getByPackageName(packageName)
    suspend fun upsertAppInfo(appInfo: AppInfo) = appInfoDao.insert(appInfo)
    suspend fun removeAppInfosByPackage(packageNames: List<String>) = appInfoDao.deleteByPackageNames(packageNames)
    suspend fun clearAllAppInfo() = appInfoDao.clearAll()

    // NotifyRouteRule
    fun getAllNotifyRouteRulesFlow(): Flow<List<NotifyRouteRule>> = notifyRouteRuleDao.getAllFlow()
    fun observeNotifySenderIds(scope: Int, packageName: String): Flow<Set<Long>> =
        notifyRouteRuleDao.observeSenderIdsByScopeAndPackage(scope, packageName).mapToSet()
    fun observeNotifyPackageNames(scope: Int, senderId: Long): Flow<Set<String>> =
        notifyRouteRuleDao.observePackageNamesByScopeAndSender(scope, senderId).mapToSet()
    suspend fun deleteNotifyRouteByScopeAndPackage(scope: Int, packageName: String) =
        notifyRouteRuleDao.deleteByScopeAndPackage(scope, packageName)
    suspend fun deleteNotifyRouteByScopesAndSender(scopes: List<Int>, senderId: Long) =
        notifyRouteRuleDao.deleteByScopesAndSender(scopes, senderId)
    suspend fun insertNotifyRouteRules(rules: List<NotifyRouteRule>) = notifyRouteRuleDao.insertAll(rules)
    suspend fun clearAllNotifyRouteRules() = notifyRouteRuleDao.clearAll()

    // ForwardFilterRule (New)
    fun getAllForwardFilterRulesFlow(): Flow<List<ForwardFilterRule>> = 
        forwardFilterRuleDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    
    fun observeForwardFiltersByScope(msgType: String, scopeType: String, scopeKey: String? = null, senderId: Long? = null): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.observeByScope(msgType, scopeType, scopeKey, senderId).map { list -> list.map { it.toDomain() } }
    
    fun observeForwardFiltersByScopePrefix(msgType: String, scopeType: String, scopeKeyPrefix: String): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.observeByScopePrefix(msgType, scopeType, scopeKeyPrefix).map { list -> list.map { it.toDomain() } }
    
    suspend fun insertForwardFilterRule(rule: ForwardFilterRule) = forwardFilterRuleDao.insert(rule.toEntity())
    suspend fun updateForwardFilterRule(rule: ForwardFilterRule) = forwardFilterRuleDao.update(rule.toEntity())
    suspend fun deleteForwardFilterRuleById(id: Long) = forwardFilterRuleDao.deleteById(id)
    suspend fun updateForwardFilterEnabled(id: Long, enabled: Int, updateTime: Long) =
        forwardFilterRuleDao.updateEnabledById(id, enabled, updateTime)
    suspend fun clearAllForwardFilterRules() = forwardFilterRuleDao.clearAll()

    // Rule (Legacy Forwarder Rule)
    fun getAllRulesFlow(): Flow<List<Rule>> = ruleDao.observeAll().map { list -> list.map { it.toDomain() } }
    fun observeRulesBySender(senderId: Long): Flow<List<Rule>> = ruleDao.observeBySender(senderId).map { list -> list.map { it.toDomain() } }
    suspend fun getAllRules(): List<Rule> = ruleDao.getAll().map { it.toDomain() }
    suspend fun getRuleById(id: Long): Rule = ruleDao.getOne(id).toDomain()
    suspend fun insertRule(rule: Rule) = ruleDao.insert(rule.toEntity())
    suspend fun updateRule(rule: Rule) = ruleDao.update(rule.toEntity())
    suspend fun deleteRule(rule: Rule) = ruleDao.delete(rule.toEntity())
    suspend fun clearAllRules() = ruleDao.deleteAll()

    // Sender
    fun getAllSendersFlow(): Flow<List<Sender>> = senderDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    suspend fun getAllSenders(): List<Sender> = senderDao.getAll().map { it.toDomain() }
    suspend fun getSenderById(id: Long): Sender? = senderDao.getOne(id)?.toDomain()
    suspend fun insertSender(sender: Sender) = senderDao.insert(sender.toEntity())
    suspend fun updateSender(sender: Sender) = senderDao.update(sender.toEntity())
    suspend fun updateSenderStatus(ids: List<Long>, status: Int) = senderDao.updateStatusByIds(ids, status)
    suspend fun deleteSender(sender: Sender) = senderDao.delete(sender.toEntity())
    suspend fun clearAllSenders() = senderDao.deleteAll()

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
}
