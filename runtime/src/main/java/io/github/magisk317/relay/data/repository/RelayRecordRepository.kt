package io.github.magisk317.relay.data.repository

import android.content.Context
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.data.db.mergeSmsMsgForInsert
import io.github.magisk317.relay.domain.pipeline.SenderDispatchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RelayRecordRepository(
    context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
    private val preferenceDataSource: PreferenceDataSource,
) {
    private val appContext = context.applicationContext ?: context

    suspend fun listRecords(limit: Int): List<SmsMsg> = db.smsMsgDao().getAll().take(limit)

    /** 观察全量记录的 Flow，Room 自动在 DB 变更时发出新列表。 */
    fun queryAllFlow(): Flow<List<SmsMsg>> = db.smsMsgDao().getAllFlow()

    /** 观察特定包名的通知日志（限额）。 */
    fun observeLogsForPackage(packageName: String, limit: Int): Flow<List<SmsMsg>> =
        db.smsMsgDao().getAllFlow().map { list ->
            list.asSequence()
                .filter { it.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY && it.packageName == packageName }
                .take(limit)
                .toList()
        }

    /** 观察最近使用的通知渠道 ID。 */
    fun observeRecentNotifyChannelIds(packageName: String, limit: Int): Flow<List<String>> =
        db.smsMsgDao().observeRecentNotifyChannelIds(packageName, SmsMsg.MSG_TYPE_APP_NOTIFY, limit)

    /** 一性读取全量记录。 */
    suspend fun queryAll(): List<SmsMsg> = db.smsMsgDao().getAll()

    /** 批量插入（用于 undo/restore 场景）。 */
    suspend fun insertList(list: List<SmsMsg>) {
        if (list.isEmpty()) return
        db.smsMsgDao().insertAll(list)
        scheduleRecordUpload("insert_list")
    }

    suspend fun insertListAndTrim(list: List<SmsMsg>, maxCount: Int) {
        if (list.isEmpty()) return
        val dao = db.smsMsgDao()
        dao.insertAll(list)
        if (maxCount <= 0) return
        val allMsgList = dao.getAll()
        if (allMsgList.size > maxCount) {
            val outdatedMsgList = allMsgList.subList(maxCount, allMsgList.size)
            dao.deleteInTx(outdatedMsgList)
        }
        scheduleRecordUpload("insert_list_trim")
    }

    /** 批量删除（同一事务内执行）。 */
    suspend fun removeList(list: List<SmsMsg>) {
        if (list.isEmpty()) return
        db.smsMsgDao().deleteInTx(list)
        scheduleRecordUpload("remove_list")
    }

    /** 获取记录总数的实时观察流。 */
    fun countFlow(): Flow<Long> = db.smsMsgDao().countFlow()

    /** 清空所有记录。 */
    suspend fun clearAll() {
        db.smsMsgDao().clearAll()
        scheduleRecordUpload("clear_all")
    }

    suspend fun deleteRecord(recordId: Long): Boolean {
        val existing = db.smsMsgDao().getById(recordId) ?: return false
        db.smsMsgDao().delete(existing)
        scheduleRecordUpload("delete_record")
        return true
    }

    fun findRecordIdByFingerprint(
        sender: String,
        body: String,
        date: Long,
        msgType: Int,
    ): Long? {
        return db.smsMsgDao()
            .getByFingerprint(sender = sender, body = body, date = date, msgType = msgType)
            ?.id
    }

    suspend fun insertRecord(
        sender: String,
        body: String,
        date: Long,
        company: String,
        smsCode: String?,
        packageName: String,
        notifyChannelId: String,
        simSlot: Int,
        subId: Int,
        contactName: String,
        phoneArea: String,
        msgType: Int,
        isCodeSms: Boolean,
        callType: Int = 0,
    ): Long? {
        return insertRecord(
            smsMsg = SmsMsg(
                sender = sender,
                body = body,
                date = date,
                company = company,
                smsCode = smsCode,
                packageName = packageName,
                notifyChannelId = notifyChannelId,
                simSlot = simSlot,
                subId = subId,
                contactName = contactName,
                phoneArea = phoneArea,
                msgType = msgType,
                callType = callType,
            ),
            isCodeSms = isCodeSms,
        )
    }

    suspend fun insertRecord(
        smsMsg: SmsMsg,
        isCodeSms: Boolean,
    ): Long? {
        val dao = db.smsMsgDao()
        trimOldRecordsIfNeeded(dao, smsMsg.msgType, isCodeSms)
        if (isCodeSms) {
            findCodeDuplicateRecordId(dao, smsMsg)?.let { duplicateId ->
                scheduleRecordUpload("skip_duplicate_code_record")
                return duplicateId
            }
        }
        val existing = dao.getByFingerprint(
            sender = smsMsg.sender,
            body = smsMsg.body,
            date = smsMsg.date,
            msgType = smsMsg.msgType,
        )
        if (existing != null) {
            dao.update(mergeSmsMsgForInsert(existing, smsMsg))
            scheduleRecordUpload("update_record")
            return existing.id
        }
        return dao.insert(smsMsg).also { scheduleRecordUpload("insert_record") }
    }

    private fun findCodeDuplicateRecordId(
        dao: io.github.magisk317.relay.data.db.dao.SmsMsgDao,
        smsMsg: SmsMsg,
    ): Long? {
        val sender = smsMsg.sender
        val body = smsMsg.body
        if (sender.isNullOrBlank() || body.isNullOrBlank()) return null

        val timestamp = if (smsMsg.date > 0) smsMsg.date else System.currentTimeMillis()
        val from = (timestamp - CODE_RECORD_DEDUP_WINDOW_MS).coerceAtLeast(0L)
        val to = timestamp + CODE_RECORD_DEDUP_WINDOW_MS

        dao.getByFingerprintInRange(
            sender = sender,
            body = body,
            msgType = smsMsg.msgType,
            dateFrom = from,
            dateTo = to,
        )?.let { return it.id }

        val code = smsMsg.smsCode
        if (code.isNullOrBlank()) return null

        if (smsMsg.simSlot >= 0) {
            dao.getBySimSlotInRange(
                simSlot = smsMsg.simSlot,
                msgType = smsMsg.msgType,
                dateFrom = from,
                dateTo = to,
            )?.let { return it.id }
        }

        val pkg = smsMsg.packageName
        if (!pkg.isNullOrBlank()) {
            dao.getByCodeAndPackageInRange(
                smsCode = code,
                packageName = pkg,
                msgType = smsMsg.msgType,
                dateFrom = from,
                dateTo = to,
            )?.let { return it.id }
        }

        val company = smsMsg.company
        if (!company.isNullOrBlank()) {
            dao.getByCodeAndCompanyInRange(
                smsCode = code,
                company = company,
                msgType = smsMsg.msgType,
                dateFrom = from,
                dateTo = to,
            )?.let { return it.id }
        }

        return null
    }

    fun persistForwardResult(
        recordId: Long,
        results: List<SenderDispatchResult>,
        defaultMessage: String,
        forceFailed: Boolean = false,
        forcedStatus: Int? = null,
    ) {
        val msgDao = db.smsMsgDao()
        val existing = msgDao.getById(recordId) ?: return
        val successResults = results.filter { it.success }
        val failedResults = results.filterNot { it.success }
        val computedStatus = when {
            forcedStatus != null -> forcedStatus
            forceFailed -> SmsMsg.FORWARD_STATUS_FAILED
            successResults.isNotEmpty() && failedResults.isNotEmpty() -> SmsMsg.FORWARD_STATUS_PARTIAL
            successResults.isNotEmpty() -> SmsMsg.FORWARD_STATUS_SUCCESS
            else -> SmsMsg.FORWARD_STATUS_NONE
        }
        val target = results.joinToString(", ") { it.senderName }.ifBlank { null }
        val message = when {
            forceFailed -> defaultMessage
            results.isNotEmpty() -> results.joinToString("\n") { result ->
                if (result.success) "${result.senderName}通道转发成功" else "${result.senderName}通道转发失败，原因：${result.message}"
            }
            else -> defaultMessage
        }.take(MAX_FORWARD_MESSAGE_LEN)
        msgDao.update(
            existing.copy(
                forwardStatus = computedStatus,
                forwardTarget = target,
                forwardMessage = message,
                forwardTime = System.currentTimeMillis(),
            ),
        )
        scheduleRecordUpload("persist_forward_result")
    }

    private suspend fun trimOldRecordsIfNeeded(
        dao: io.github.magisk317.relay.data.db.dao.SmsMsgDao,
        msgType: Int,
        isCodeSms: Boolean,
    ) {
        val limit = getHistoryLimit(msgType, isCodeSms)
        if (limit <= 0) return
        val matching = dao.getAll()
            .asSequence()
            .filter { recordMatchesType(it, msgType, isCodeSms) }
            .sortedBy { it.date }
            .toList()
        if (matching.size < limit) return
        val deleteCount = matching.size - limit + 1
        dao.deleteInTx(matching.take(deleteCount))
    }

    private suspend fun getHistoryLimit(msgType: Int, isCodeSms: Boolean): Int {
        val key = when (msgType) {
            SmsMsg.MSG_TYPE_APP_NOTIFY -> PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY
            SmsMsg.MSG_TYPE_CALL_NOTIFY -> PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY
            SmsMsg.MSG_TYPE_SMS -> if (isCodeSms) PrefConst.KEY_HISTORY_LIMIT_CODE else PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS
            else -> PrefConst.KEY_HISTORY_LIMIT_CODE
        }
        val previousLimit = preferenceDataSource.getString(PrefConst.KEY_HISTORY_LIMIT, "0")
        val value = preferenceDataSource.getString(key, previousLimit)
        return value.toIntOrNull() ?: 0
    }

    private fun recordMatchesType(record: SmsMsg, msgType: Int, isCodeSms: Boolean): Boolean = when (msgType) {
        SmsMsg.MSG_TYPE_APP_NOTIFY -> record.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY
        SmsMsg.MSG_TYPE_SMS -> {
            val hasCode = !record.smsCode.isNullOrBlank()
            record.msgType == SmsMsg.MSG_TYPE_SMS && if (isCodeSms) hasCode else !hasCode
        }
        else -> record.msgType == msgType
    }

    private fun scheduleRecordUpload(reason: String) {
        RuntimeGraph.from(appContext).remoteAgentRepository.scheduleRecordUpload(reason)
    }

    private companion object {
        private const val MAX_FORWARD_MESSAGE_LEN = 2000
        private const val CODE_RECORD_DEDUP_WINDOW_MS = 5_000L
    }
}
