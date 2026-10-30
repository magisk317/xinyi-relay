package io.github.magisk317.relay.data.repository

import android.content.Context
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.platform.ipc.ForwardDatabaseBootstrapper
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.dao.SmsCodeRuleDao
import io.github.magisk317.smscode.rule.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.rule.repository.SmsCodeRuleRecord
import io.github.magisk317.smscode.rule.repository.SmsCodeRuleRepository
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toEntity
import io.github.magisk317.relay.android.data.mapper.ForwardConfigMapper.toDomain
import io.github.magisk317.relay.android.data.mapper.ForwardConfigMapper.toForwardEntity
import io.github.magisk317.forward.engine.model.AppInfoData
import io.github.magisk317.forward.engine.model.ForwardFilterRule
import io.github.magisk317.forward.engine.model.NotifyRouteRuleData
import io.github.magisk317.forward.engine.model.Rule
import io.github.magisk317.forward.engine.model.Sender
import io.github.magisk317.forward.engine.model.SmsCodeRuleData
import io.github.magisk317.forward.engine.service.AppConfigRepository
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardAppInfoDao
import io.github.magisk317.forward.db.ForwardFilterRuleDao
import io.github.magisk317.forward.db.ForwardRouteRuleDao
import io.github.magisk317.forward.db.ForwardRuleDao
import io.github.magisk317.forward.db.ForwardSenderDao
import io.github.magisk317.forward.engine.service.SenderRuntimeServiceRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class ConfigRepository(
    context: Context,
    private val db: AppDatabase,
    private val forwardDb: ForwardDatabase,
    private val smsCodeRuleDao: SmsCodeRuleDao,
    private val forwardAppInfoDao: ForwardAppInfoDao,
    private val forwardRouteRuleDao: ForwardRouteRuleDao,
    private val forwardFilterRuleDao: ForwardFilterRuleDao,
    private val forwardRuleDao: ForwardRuleDao,
    private val forwardSenderDao: ForwardSenderDao,
) : AppConfigRepository, SmsCodeRuleRepository {
    private val appContext = context.applicationContext ?: context

    private fun validateSmsCodeRule(rule: SmsCodeRuleData) {
        val regex = rule.codeRegex
        if (regex.isNotBlank()) {
            runCatching { Regex(regex) }.onFailure {
                throw IllegalArgumentException("Invalid regex in SMS code rule: $regex", it)
            }
        }
    }

    private fun validateAppInfo(appInfo: AppInfoData) {
        require(appInfo.packageName.isNotBlank()) { "AppInfo packageName must not be blank" }
    }

    // Legacy SmsCodeRule
    override fun observeSmsCodeRulesFlow(): Flow<List<SmsCodeRuleData>> = smsCodeRuleDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    override suspend fun getAllSmsCodeRules(): List<SmsCodeRuleData> = smsCodeRuleDao.getAll().map { it.toDomain() }
    override suspend fun getSmsCodeRuleById(id: Long): SmsCodeRuleData? = smsCodeRuleDao.getById(id)?.toDomain()
    override suspend fun upsertSmsCodeRule(rule: SmsCodeRuleData): Long {
        validateSmsCodeRule(rule)
        val result = smsCodeRuleDao.insert(rule.toEntity())
        noteMutation("config.sms_code_rule_upsert")
        return result
    }
    override suspend fun deleteSmsCodeRule(rule: SmsCodeRuleData) {
        smsCodeRuleDao.delete(rule.toEntity())
        noteMutation("config.sms_code_rule_delete")
    }
    override suspend fun insertSmsCodeRules(rules: List<SmsCodeRuleData>) {
        smsCodeRuleDao.insertAll(rules.map { it.toEntity() })
        noteMutation("config.sms_code_rules_replace")
    }
    override suspend fun clearAllSmsCodeRules() {
        smsCodeRuleDao.clearAll()
        noteMutation("config.sms_code_rules_clear")
    }

    // --- the shared rule storage contract -----------------------------------
    // Same DAO as the app-specific methods above; the contract exists so both apps
    // hold rules through one interface. checkpoint() is inherited: relay already
    // records a mutation on every write, so there is nothing left to publish.
    override fun observeRules(): Flow<List<SmsCodeRuleRecord>> =
        observeSmsCodeRulesFlow().map { list -> list.map { it.toRecord() } }

    override suspend fun getAll(): List<SmsCodeRuleRecord> =
        getAllSmsCodeRules().map { it.toRecord() }

    override suspend fun getById(id: Long): SmsCodeRuleRecord? =
        getSmsCodeRuleById(id)?.toRecord()

    override suspend fun upsert(record: SmsCodeRuleRecord): Long =
        upsertSmsCodeRule(record.toData())

    override suspend fun delete(record: SmsCodeRuleRecord) {
        deleteSmsCodeRule(record.toData())
    }

    override suspend fun insertAll(records: List<SmsCodeRuleRecord>) {
        insertSmsCodeRules(records.map { it.toData() })
    }

    override suspend fun clearAll() {
        clearAllSmsCodeRules()
    }

    // AppInfo
    override fun getAllAppInfoFlow(): Flow<List<AppInfoData>> =
        forwardAppInfoDao.observeAll().awaitForwardImport().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllAppInfo(): List<AppInfoData> = awaitForwardImport {
        forwardAppInfoDao.all().map { it.toDomain() }
    }

    override suspend fun getAppInfoByPackage(packageName: String): AppInfoData? = awaitForwardImport {
        forwardAppInfoDao.byPackage(packageName)?.toDomain()
    }

    override suspend fun upsertAppInfo(appInfo: AppInfoData) {
        validateAppInfo(appInfo)
        awaitForwardImport { forwardAppInfoDao.upsert(appInfo.toForwardEntity()) }
        noteMutation("config.app_info_upsert")
    }

    override suspend fun removeAppInfosByPackage(packageNames: List<String>) {
        awaitForwardImport { forwardAppInfoDao.deleteByPackageNames(packageNames) }
        noteMutation("config.app_info_delete")
    }

    override suspend fun clearAllAppInfo() {
        awaitForwardImport { forwardAppInfoDao.clear() }
        noteMutation("config.app_info_clear")
    }

    // NotifyRouteRule
    override fun getAllNotifyRouteRulesFlow(): Flow<List<NotifyRouteRuleData>> =
        forwardRouteRuleDao.observeAll().awaitForwardImport().map { list -> list.map { it.toDomain() } }

    override fun observeNotifySenderIds(scope: Int, packageName: String): Flow<Set<Long>> =
        forwardRouteRuleDao.observeSenderIdsByScopeAndPackage(scope, packageName).awaitForwardImport().mapToSet()

    override fun observeNotifyPackageNames(scope: Int, senderId: Long): Flow<Set<String>> =
        forwardRouteRuleDao.observePackageNamesByScopeAndSender(scope, senderId).awaitForwardImport().mapToSet()

    override suspend fun deleteNotifyRouteByScopeAndPackage(scope: Int, packageName: String) {
        awaitForwardImport { forwardRouteRuleDao.deleteByScopeAndPackage(scope, packageName) }
        noteMutation("config.notify_route_delete_by_package")
    }

    override suspend fun deleteNotifyRouteByScopesAndSender(scopes: List<Int>, senderId: Long) {
        awaitForwardImport { forwardRouteRuleDao.deleteByScopesAndSender(scopes, senderId) }
        noteMutation("config.notify_route_delete_by_sender")
    }

    override suspend fun insertNotifyRouteRules(rules: List<NotifyRouteRuleData>) {
        awaitForwardImport { forwardRouteRuleDao.upsertAll(rules.map { it.toForwardEntity() }) }
        noteMutation("config.notify_routes_replace")
    }

    override suspend fun clearAllNotifyRouteRules() {
        awaitForwardImport { forwardRouteRuleDao.clear() }
        noteMutation("config.notify_routes_clear")
    }

    // ForwardFilterRule (New)
    override fun getAllForwardFilterRulesFlow(): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.observeAll().awaitForwardImport().map { list -> list.map { it.toDomain() } }

    override fun observeForwardFiltersByScope(
        msgType: String,
        scopeType: String,
        scopeKey: String?,
        senderId: Long?,
    ): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.observeByScope(msgType, scopeType, scopeKey, senderId)
            .awaitForwardImport()
            .map { list -> list.map { it.toDomain() } }

    override fun observeForwardFiltersByScopePrefix(
        msgType: String,
        scopeType: String,
        scopeKeyPrefix: String,
    ): Flow<List<ForwardFilterRule>> =
        forwardFilterRuleDao.observeByScopePrefix(msgType, scopeType, scopeKeyPrefix)
            .awaitForwardImport()
            .map { list -> list.map { it.toDomain() } }

    override suspend fun insertForwardFilterRule(rule: ForwardFilterRule) {
        awaitForwardImport { forwardFilterRuleDao.upsert(rule.toForwardEntity()) }
        noteMutation("config.forward_filter_insert")
    }

    override suspend fun updateForwardFilterRule(rule: ForwardFilterRule) {
        awaitForwardImport { forwardFilterRuleDao.update(rule.toForwardEntity()) }
        noteMutation("config.forward_filter_update")
    }

    override suspend fun deleteForwardFilterRuleById(id: Long) {
        awaitForwardImport { forwardFilterRuleDao.deleteById(id) }
        noteMutation("config.forward_filter_delete")
    }

    override suspend fun updateForwardFilterEnabled(id: Long, enabled: Int, updateTime: Long) {
        awaitForwardImport { forwardFilterRuleDao.updateEnabledById(id, enabled, updateTime) }
        noteMutation("config.forward_filter_toggle")
    }

    override suspend fun clearAllForwardFilterRules() {
        awaitForwardImport { forwardFilterRuleDao.clearAll() }
        noteMutation("config.forward_filter_clear")
    }

    // Rule (Legacy Forwarder Rule)
    override fun getAllRulesFlow(): Flow<List<Rule>> =
        forwardRuleDao.observeAll().awaitForwardImport().map { list -> list.map { it.toDomain() } }

    override fun observeRulesBySender(senderId: Long): Flow<List<Rule>> =
        forwardRuleDao.observeBySender(senderId).awaitForwardImport().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllRules(): List<Rule> = awaitForwardImport {
        forwardRuleDao.all().map { it.toDomain() }
    }

    override suspend fun getRuleById(id: Long): Rule = awaitForwardImport {
        forwardRuleDao.byId(id).toDomain()
    }

    override suspend fun insertRule(rule: Rule) {
        awaitForwardImport { forwardRuleDao.insert(rule.toForwardEntity()) }
        noteMutation("config.rule_insert")
    }

    override suspend fun updateRule(rule: Rule) {
        awaitForwardImport { forwardRuleDao.update(rule.toForwardEntity()) }
        noteMutation("config.rule_update")
    }

    override suspend fun deleteRule(rule: Rule) {
        awaitForwardImport { forwardRuleDao.delete(rule.toForwardEntity()) }
        noteMutation("config.rule_delete")
    }

    override suspend fun clearAllRules() {
        awaitForwardImport { forwardRuleDao.clear() }
        noteMutation("config.rule_clear")
    }

    // Sender
    override fun getAllSendersFlow(): Flow<List<Sender>> = forwardSenderDao.observeAll()
        .awaitForwardImport()
        .map { list ->
            val result = ArrayList<Sender>(list.size)
            list.forEach { entity ->
                result += sanitizeAndPersistSenderRepair(entity.toDomain())
            }
            result
        }

    override suspend fun getAllSenders(): List<Sender> = awaitForwardImport {
        val list = forwardSenderDao.all()
        val result = ArrayList<Sender>(list.size)
        list.forEach { entity ->
            result += sanitizeAndPersistSenderRepair(entity.toDomain())
        }
        result
    }

    override suspend fun getSenderById(id: Long): Sender? = awaitForwardImport {
        forwardSenderDao.byId(id)?.toDomain()?.let {
            sanitizeAndPersistSenderRepair(it)
        }
    }

    override suspend fun insertSender(sender: Sender): Long {
        val safeSender = sanitizeSender(sender)
        val id = awaitForwardImport { forwardSenderDao.upsert(safeSender.toForwardEntity()) }
        noteMutation("config.sender_insert")
        return id
    }

    override suspend fun updateSender(sender: Sender) {
        awaitForwardImport { forwardSenderDao.update(sanitizeSender(sender).toForwardEntity()) }
        noteMutation("config.sender_update")
    }

    override suspend fun updateSenderStatus(ids: List<Long>, status: Int) {
        awaitForwardImport { forwardSenderDao.updateStatusByIds(ids, status) }
        noteMutation("config.sender_status")
    }

    override suspend fun updateSenderPriorities(priorityById: Map<Long, Int>) {
        awaitForwardImport {
            priorityById.forEach { (id, priority) ->
                forwardSenderDao.updatePriorityById(id, priority)
            }
        }
        noteMutation("config.sender_priority")
    }

    override suspend fun deleteSender(sender: Sender) {
        awaitForwardImport { forwardSenderDao.delete(sender.toForwardEntity()) }
        noteMutation("config.sender_delete")
    }

    override suspend fun clearAllSenders() {
        awaitForwardImport { forwardSenderDao.clear() }
        noteMutation("config.sender_clear")
    }

    /** 手动触发 WAL Checkpoint，确保 UI 进程写入的数据对 Hook 进程可见。 */
    override suspend fun checkpoint() {
        try {
            db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .close()
            forwardDb.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .close()
        } catch (_: Throwable) {
        }
    }

    private fun <T> Flow<List<T>>.mapToSet(): Flow<Set<T>> = map { it.toSet() }

    private suspend fun <T> awaitForwardImport(block: suspend () -> T): T {
        ForwardDatabaseBootstrapper.awaitReady()
        return block()
    }

    private fun <T> Flow<T>.awaitForwardImport(): Flow<T> = onStart {
        ForwardDatabaseBootstrapper.awaitReady()
    }

    private suspend fun noteMutation(source: String) {
        runCatching { RuntimeDependencies.get().localConfigRepository.noteLocalMutation(source) }
            .onFailure { XLog.e(message = "noteLocalMutation failed: %s", it.message ?: it.javaClass.simpleName) }
        runCatching { RuntimeDependencies.get().autoBackupTrigger.scheduleAutoBackup(source) }
            .onFailure { XLog.e(message = "scheduleAutoBackup failed: %s", it.message ?: it.javaClass.simpleName) }
    }

    private suspend fun sanitizeAndPersistSenderRepair(sender: Sender): Sender {
        ForwardDatabaseBootstrapper.awaitReady()
        val safeSender = sanitizeSender(sender)
        if (safeSender != sender) {
            forwardSenderDao.update(safeSender.toForwardEntity())
            XLog.i(message = "Sender config repaired id=%d type=%d", safeSender.id, safeSender.type)
            noteMutation("config.sender_repair")
        }
        return safeSender
    }

    private fun sanitizeSender(sender: Sender): Sender {
        return SenderRuntimeServiceRegistry.requireInstalled()
            .configSanitizer
            .sanitizeSenderLenient(sender)
    }
}


private fun SmsCodeRuleData.toRecord(): SmsCodeRuleRecord = SmsCodeRuleRecord(
    id = id,
    spec = SmsCodeRuleSpec(
        company = company,
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
        source = SmsCodeMatchedRuleSource.CUSTOM,
    ),
)

private fun SmsCodeRuleRecord.toData(): SmsCodeRuleData = object : SmsCodeRuleData {
    override val id: Long = this@toData.id
    override val company: String? = spec.company
    override val codeKeyword: String = spec.codeKeyword
    override val codeRegex: String = spec.codeRegex
}
