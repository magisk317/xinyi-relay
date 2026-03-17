package io.github.magisk317.relay.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.DBProvider
import io.github.magisk317.relay.data.db.entity.SmsMsg
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
    }

    /** 批量删除（同一事务内执行）。 */
    suspend fun removeList(list: List<SmsMsg>) {
        if (list.isEmpty()) return
        db.smsMsgDao().deleteInTx(list)
    }

    /** 获取记录总数的实时观察流。 */
    fun countFlow(): Flow<Long> = db.smsMsgDao().countFlow()

    /** 清空所有记录。 */
    suspend fun clearAll() = db.smsMsgDao().clearAll()


    suspend fun deleteRecord(recordId: Long): Boolean {
        val existing = db.smsMsgDao().getById(recordId) ?: return false
        db.smsMsgDao().delete(existing)
        return true
    }

    fun findRecordIdByFingerprint(
        sender: String,
        body: String,
        date: Long,
        msgType: Int,
    ): Long? {
        val resolver = appContext.contentResolver
        val smsMsgUri = DBProvider.SMS_MSG_CONTENT_URI
        val projection = arrayOf("_id", "sender", "body", "date", "msg_type")
        return runCatching {
            resolver.query(smsMsgUri, projection, null, null, "date DESC")?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val senderIdx = cursor.getColumnIndex("sender")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")
                val msgTypeIdx = cursor.getColumnIndex("msg_type")
                while (cursor.moveToNext()) {
                    val senderValue = if (senderIdx >= 0) cursor.getString(senderIdx) else null
                    val bodyValue = if (bodyIdx >= 0) cursor.getString(bodyIdx) else null
                    val dateValue = if (dateIdx >= 0) cursor.getLong(dateIdx) else -1L
                    val msgTypeValue = if (msgTypeIdx >= 0) cursor.getInt(msgTypeIdx) else SmsMsg.MSG_TYPE_SMS
                    if (senderValue == sender && bodyValue == body && dateValue == date && msgTypeValue == msgType) {
                        return if (idIdx >= 0) cursor.getLong(idIdx) else null
                    }
                }
                null
            }
        }.getOrNull()
    }

    suspend fun insertRecord(
        sender: String,
        body: String,
        date: Long,
        company: String,
        smsCode: String?,
        packageName: String,
        notifyChannelId: String,
        msgType: Int,
        isCodeSms: Boolean,
        callType: Int = 0,
    ): Long? {
        val resolver = appContext.contentResolver
        val smsMsgUri = DBProvider.SMS_MSG_CONTENT_URI
        trimOldRecordsIfNeeded(resolver, msgType, isCodeSms)
        val values = ContentValues().apply {
            put("body", body)
            put("company", company)
            put("date", date)
            put("sender", sender)
            put("sms_code", smsCode)
            put("package_name", packageName)
            put("notify_channel_id", notifyChannelId)
            put("msg_type", msgType)
            put("call_type", callType)
        }
        return resolver.insert(smsMsgUri, values)?.lastPathSegment?.toLongOrNull()
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
            else -> SmsMsg.FORWARD_STATUS_FAILED
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
    }

    private suspend fun trimOldRecordsIfNeeded(
        resolver: ContentResolver,
        msgType: Int,
        isCodeSms: Boolean,
    ) {
        val smsMsgUri = DBProvider.SMS_MSG_CONTENT_URI
        val (selection, selectionArgs) = recordSelectionForType(msgType, isCodeSms)
        val cursor = resolver.query(smsMsgUri, arrayOf("_id"), selection, selectionArgs, "date ASC") ?: return
        cursor.use {
            val count = it.count
            val limit = getHistoryLimit(msgType, isCodeSms)
            if (limit <= 0 || count < limit) return
            val operations = ArrayList<android.content.ContentProviderOperation>()
            for (i in 0 until (count - limit + 1)) {
                if (!it.moveToNext()) break
                val id = it.getLong(0)
                operations += android.content.ContentProviderOperation.newDelete(smsMsgUri)
                    .withSelection("_id = ?", arrayOf(id.toString()))
                    .build()
            }
            if (operations.isNotEmpty()) {
                resolver.applyBatch(DBProvider.AUTHORITY, operations)
            }
        }
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

    private fun recordSelectionForType(msgType: Int, isCodeSms: Boolean): Pair<String, Array<String>> {
        return when (msgType) {
            SmsMsg.MSG_TYPE_APP_NOTIFY -> "msg_type = ?" to arrayOf(SmsMsg.MSG_TYPE_APP_NOTIFY.toString())
            SmsMsg.MSG_TYPE_SMS -> {
                if (isCodeSms) {
                    "msg_type = ? AND sms_code IS NOT NULL AND sms_code != ''" to arrayOf(SmsMsg.MSG_TYPE_SMS.toString())
                } else {
                    "msg_type = ? AND (sms_code IS NULL OR sms_code = '')" to arrayOf(SmsMsg.MSG_TYPE_SMS.toString())
                }
            }

            else -> "msg_type = ?" to arrayOf(msgType.toString())
        }
    }

    private companion object {
        private const val MAX_FORWARD_MESSAGE_LEN = 2000
    }
}
