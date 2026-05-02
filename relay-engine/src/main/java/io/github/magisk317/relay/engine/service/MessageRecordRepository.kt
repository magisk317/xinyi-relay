package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.engine.model.ReadRecordData
import kotlinx.coroutines.flow.Flow

interface MessageRecordRepository {
    suspend fun listRecords(limit: Int): List<ReadRecordData>
    fun queryAllFlow(): Flow<List<ReadRecordData>>
    fun observeLogsForPackage(packageName: String, limit: Int): Flow<List<ReadRecordData>>
    fun observeRecentNotifyChannelIds(packageName: String, limit: Int): Flow<List<String>>
    suspend fun queryAll(): List<ReadRecordData>
    suspend fun insertList(list: List<ReadRecordData>)
    suspend fun insertListAndTrim(list: List<ReadRecordData>, maxCount: Int)
    suspend fun removeList(list: List<ReadRecordData>)
    fun countFlow(): Flow<Long>
    suspend fun clearAll()
    suspend fun deleteRecord(recordId: Long): Boolean
    suspend fun findRecordIdByFingerprint(sender: String, body: String, date: Long, msgType: Int): Long?
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
        callType: Int,
        sessionKey: String,
    ): Long?
    suspend fun persistForwardResult(
        recordId: Long,
        results: List<SenderDispatchResult>,
        defaultMessage: String,
        forceFailed: Boolean,
        forcedStatus: Int?,
    )
    fun buildCallSessionKey(sender: String?, body: String?, callType: Int, packageName: String?): String
}