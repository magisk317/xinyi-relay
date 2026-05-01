package io.github.magisk317.relay.android.data.db.dao

import androidx.room.*
import io.github.magisk317.relay.android.data.db.entity.ForwardFilterRuleEntity
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.db.entity.AutoInputEvent
import io.github.magisk317.relay.android.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsCodeRuleDao {
    @Query("SELECT * FROM sms_code_rule")
    suspend fun getAll(): List<SmsCodeRule>

    @Query("SELECT * FROM sms_code_rule WHERE id = :id")
    suspend fun getById(id: Long): SmsCodeRule?

    @Query("SELECT * FROM sms_code_rule")
    fun getAllFlow(): Flow<List<SmsCodeRule>>

    @Query(
        "SELECT * FROM sms_code_rule WHERE company = :company AND code_keyword = :codeKeyword AND code_regex = :codeRegex",
    )
    suspend fun queryRules(company: String?, codeKeyword: String, codeRegex: String): List<SmsCodeRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SmsCodeRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<SmsCodeRule>)

    @Update
    suspend fun update(rule: SmsCodeRule)

    @Delete
    suspend fun delete(rule: SmsCodeRule)

    @Delete
    suspend fun deleteAll(rules: List<SmsCodeRule>)

    @Query("DELETE FROM sms_code_rule")
    suspend fun clearAll()

    @Query("SELECT count(*) FROM sms_code_rule")
    suspend fun count(): Long
}

@Dao
interface SmsMsgDao {
    @Query("SELECT * FROM sms_msg ORDER BY date DESC")
    suspend fun getAll(): List<SmsMsg>

    @Query("SELECT * FROM sms_msg WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsMsg?

    @Query("SELECT * FROM sms_msg WHERE sender IS :sender AND body IS :body AND date = :date AND msg_type = :msgType LIMIT 1")
    suspend fun getByFingerprint(sender: String?, body: String?, date: Long, msgType: Int): SmsMsg?

    @Query("SELECT * FROM sms_msg WHERE msg_type = :msgType AND session_key = :sessionKey LIMIT 1")
    fun getBySessionKey(msgType: Int, sessionKey: String): SmsMsg?

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sender IS :sender AND body IS :body " +
            "AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    fun getByFingerprintInRange(
        sender: String?,
        body: String?,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): SmsMsg?

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sms_code IS :smsCode AND company IS :company " +
            "AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    fun getByCodeAndCompanyInRange(
        smsCode: String?,
        company: String?,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): SmsMsg?

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sms_code IS :smsCode AND package_name IS :packageName " +
            "AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    fun getByCodeAndPackageInRange(
        smsCode: String?,
        packageName: String?,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): SmsMsg?

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sms_code IS :smsCode " +
            "AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo",
    )
    suspend fun getByCodeInRange(
        smsCode: String?,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): List<SmsMsg>

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sim_slot = :simSlot AND msg_type = :msgType " +
            "AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    suspend fun getBySimSlotInRange(
        simSlot: Int,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): SmsMsg?

    @Query("SELECT * FROM sms_msg ORDER BY date DESC")
    fun getAllFlow(): Flow<List<SmsMsg>>

    @Query(
        "SELECT notify_channel_id FROM sms_msg " +
            "WHERE msg_type = :msgType " +
            "AND package_name = :packageName " +
            "AND notify_channel_id != '' " +
            "GROUP BY notify_channel_id " +
            "ORDER BY MAX(date) DESC " +
            "LIMIT :limit",
    )
    suspend fun queryRecentNotifyChannelIds(
        packageName: String,
        msgType: Int = SmsMsg.MSG_TYPE_APP_NOTIFY,
        limit: Int = 20,
    ): List<String>

    @Query(
        "SELECT notify_channel_id FROM sms_msg " +
            "WHERE msg_type = :msgType " +
            "AND package_name = :packageName " +
            "AND notify_channel_id != '' " +
            "GROUP BY notify_channel_id " +
            "ORDER BY MAX(date) DESC " +
            "LIMIT :limit",
    )
    fun observeRecentNotifyChannelIds(
        packageName: String,
        msgType: Int = SmsMsg.MSG_TYPE_APP_NOTIFY,
        limit: Int = 20,
    ): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: SmsMsg): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(msgs: List<SmsMsg>)

    @Update
    suspend fun update(msg: SmsMsg)

    @Query("DELETE FROM sms_msg")
    suspend fun clearAll()

    @Query("SELECT count(*) FROM sms_msg")
    suspend fun count(): Long

    @Query("SELECT count(*) FROM sms_msg")
    fun countFlow(): Flow<Long>

    @Query("SELECT count(*) FROM sms_msg WHERE date >= :fromMs")
    suspend fun countFrom(fromMs: Long): Long

    @Query(
        "SELECT count(*) FROM sms_msg " +
            "WHERE msg_type = :msgType " +
            "AND sms_code IS NOT NULL AND sms_code != '' " +
            "AND date >= :fromMs",
    )
    suspend fun countCodeSmsFrom(fromMs: Long, msgType: Int = SmsMsg.MSG_TYPE_SMS): Long

    @Delete
    suspend fun delete(msg: SmsMsg)

    @Delete
    suspend fun deleteInTx(msgs: List<SmsMsg>)
}

data class SenderDispatchStatRow(
    val senderType: Int,
    val sent: Long,
    val success: Long,
    val failed: Long,
)

@Dao
interface SenderDispatchLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SenderDispatchLog): Long

    @Query("SELECT COUNT(*) FROM sender_dispatch_log WHERE created_at >= :fromMs")
    suspend fun countTotalFrom(fromMs: Long): Long

    @Query("SELECT COUNT(*) FROM sender_dispatch_log WHERE created_at >= :fromMs AND success = 1")
    suspend fun countSuccessFrom(fromMs: Long): Long

    @Query("SELECT COUNT(*) FROM sender_dispatch_log WHERE created_at >= :fromMs AND success = 0")
    suspend fun countFailedFrom(fromMs: Long): Long

    @Query(
        "SELECT sender_type AS senderType, " +
            "COUNT(*) AS sent, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS success, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failed " +
            "FROM sender_dispatch_log " +
            "WHERE created_at >= :fromMs " +
            "GROUP BY sender_type",
    )
    suspend fun aggregateBySenderType(fromMs: Long): List<SenderDispatchStatRow>
}

@Dao
interface AutoInputEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AutoInputEvent): Long

    @Query("UPDATE auto_input_event SET success = :success, fail_reason = :reason WHERE id = :id")
    suspend fun updateResult(id: Long, success: Boolean, reason: String?): Int

    @Query("SELECT COUNT(*) FROM auto_input_event WHERE attempt_at >= :fromMs")
    suspend fun countAttempts(fromMs: Long): Long

    @Query("SELECT COUNT(*) FROM auto_input_event WHERE attempt_at >= :fromMs AND success = 1")
    suspend fun countSuccess(fromMs: Long): Long

    @Query("SELECT COUNT(*) FROM auto_input_event WHERE attempt_at >= :fromMs AND success = 0")
    suspend fun countFailed(fromMs: Long): Long
}

@Dao
interface AppInfoDao {
    @Query("SELECT * FROM app_info")
    suspend fun getAll(): List<AppInfo>

    @Query("SELECT * FROM app_info")
    fun getAllFlow(): Flow<List<AppInfo>>

    @Query("SELECT * FROM app_info WHERE blocked = 1")
    suspend fun getBlockedApps(): List<AppInfo>

    @Query("SELECT * FROM app_info WHERE forwarding = 1")
    suspend fun getForwardingApps(): List<AppInfo>

    @Query("SELECT * FROM app_info WHERE package_name = :packageName")
    suspend fun getByPackageName(packageName: String): AppInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appInfo: AppInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appInfos: List<AppInfo>)

    @Update
    suspend fun update(appInfo: AppInfo)

    @Delete
    suspend fun delete(appInfo: AppInfo)

    @Delete
    suspend fun deleteInTx(appInfos: List<AppInfo>)

    @Query("DELETE FROM app_info WHERE package_name IN (:packageNames)")
    suspend fun deleteByPackageNames(packageNames: List<String>): Int

    @Query("DELETE FROM app_info")
    suspend fun clearAll()
}

@Dao
interface NotifyRouteRuleDao {
    @Query("SELECT * FROM notify_route_rule")
    suspend fun getAll(): List<NotifyRouteRule>

    @Query("SELECT * FROM notify_route_rule")
    fun getAllFlow(): Flow<List<NotifyRouteRule>>

    @Query("SELECT sender_id FROM notify_route_rule WHERE scope = :scope AND package_name = :packageName")
    suspend fun getSenderIdsByScopeAndPackage(scope: Int, packageName: String): List<Long>

    @Query("SELECT sender_id FROM notify_route_rule WHERE scope = :scope AND package_name = :packageName")
    fun observeSenderIdsByScopeAndPackage(scope: Int, packageName: String): Flow<List<Long>>

    @Query("SELECT package_name FROM notify_route_rule WHERE scope = :scope AND sender_id = :senderId")
    suspend fun getPackageNamesByScopeAndSender(scope: Int, senderId: Long): List<String>

    @Query("SELECT package_name FROM notify_route_rule WHERE scope = :scope AND sender_id = :senderId")
    fun observePackageNamesByScopeAndSender(scope: Int, senderId: Long): Flow<List<String>>

    @Query("SELECT DISTINCT sender_id FROM notify_route_rule WHERE scope = :scope AND sender_id IN (:senderIds)")
    suspend fun getDistinctSenderIdsByScopeIn(scope: Int, senderIds: List<Long>): List<Long>

    @Query("DELETE FROM notify_route_rule WHERE scope = :scope AND package_name = :packageName")
    suspend fun deleteByScopeAndPackage(scope: Int, packageName: String): Int

    @Query("DELETE FROM notify_route_rule WHERE scope = :scope AND sender_id = :senderId")
    suspend fun deleteByScopeAndSender(scope: Int, senderId: Long): Int

    @Query("DELETE FROM notify_route_rule WHERE scope IN (:scopes) AND sender_id = :senderId")
    suspend fun deleteByScopesAndSender(scopes: List<Int>, senderId: Long): Int

    @Query("DELETE FROM notify_route_rule")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: NotifyRouteRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<NotifyRouteRule>)
}

@Dao
interface ForwardFilterRuleDao {
    @Query("SELECT * FROM forward_filter_rule ORDER BY id DESC")
    suspend fun getAll(): List<ForwardFilterRuleEntity>

    @Query("SELECT * FROM forward_filter_rule ORDER BY id DESC")
    fun getAllFlow(): Flow<List<ForwardFilterRuleEntity>>

    @Query("SELECT * FROM forward_filter_rule WHERE msg_type = :msgType AND enabled = 1 ORDER BY id DESC")
    suspend fun getEnabledByMsgType(msgType: String): List<ForwardFilterRuleEntity>

    @Query(
        "SELECT * FROM forward_filter_rule " +
            "WHERE msg_type = :msgType " +
            "AND scope_type = :scopeType " +
            "AND (:scopeKey IS NULL OR scope_key = :scopeKey) " +
            "AND (:senderId IS NULL OR sender_id = :senderId) " +
            "ORDER BY id DESC",
    )
    fun observeByScope(
        msgType: String,
        scopeType: String,
        scopeKey: String? = null,
        senderId: Long? = null,
    ): Flow<List<ForwardFilterRuleEntity>>

    @Query(
        "SELECT * FROM forward_filter_rule " +
            "WHERE msg_type = :msgType " +
            "AND scope_type = :scopeType " +
            "AND scope_key LIKE :scopeKeyPrefix " +
            "ORDER BY id DESC",
    )
    fun observeByScopePrefix(
        msgType: String,
        scopeType: String,
        scopeKeyPrefix: String,
    ): Flow<List<ForwardFilterRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ForwardFilterRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ForwardFilterRuleEntity>)

    @Update
    suspend fun update(rule: ForwardFilterRuleEntity)

    @Delete
    suspend fun delete(rule: ForwardFilterRuleEntity)

    @Query("DELETE FROM forward_filter_rule WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE forward_filter_rule SET enabled = :enabled, update_time = :updateTime WHERE id = :id")
    suspend fun updateEnabledById(id: Long, enabled: Int, updateTime: Long): Int

    @Query("DELETE FROM forward_filter_rule")
    suspend fun clearAll()
}
