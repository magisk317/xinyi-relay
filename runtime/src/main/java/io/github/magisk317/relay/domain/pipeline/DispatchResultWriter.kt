package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.engine.service.SenderDispatchResult
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog
import io.github.magisk317.relay.data.repository.RelayRecordRepository

class DispatchResultWriter(
    private val db: AppDatabase,
    private val relayRecordRepository: RelayRecordRepository,
    private val preferenceDataSource: PreferenceDataSource,
) {
    suspend fun persistForwardResult(
        recordId: Long?,
        results: List<SenderDispatchResult>,
        defaultMessage: String,
        forceFailed: Boolean = false,
        forcedStatus: Int? = null,
        msgTypeForAnalytics: Int? = null,
    ) {
        if (recordId == null) return
        runCatching {
            relayRecordRepository.persistForwardResult(
                recordId = recordId,
                results = results,
                defaultMessage = defaultMessage,
                forceFailed = forceFailed,
                forcedStatus = forcedStatus,
            )
            if (msgTypeForAnalytics != null && preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_ANALYTICS, true)) {
                persistSenderDispatchLogs(recordId, msgTypeForAnalytics, results)
            }
        }.onFailure { error ->
            XLog.w("persistForwardResult failed: %s", error.message ?: error.javaClass.simpleName)
        }
    }

    suspend fun findRecordIdByFingerprint(
        sender: String,
        body: String,
        date: Long,
        msgType: Int,
    ): Long? {
        return relayRecordRepository.findRecordIdByFingerprint(sender, body, date, msgType)
    }

    fun buildCallSessionKey(
        sender: String?,
        body: String?,
        callType: Int,
        packageName: String?,
    ): String {
        return relayRecordRepository.buildCallSessionKey(
            sender = sender,
            body = body,
            callType = callType,
            packageName = packageName,
        )
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
        sessionKey: String = "",
    ): Long? {
        return relayRecordRepository.insertRecord(
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
            isCodeSms = isCodeSms,
            callType = callType,
            sessionKey = sessionKey,
        )
    }

    private suspend fun persistSenderDispatchLogs(
        recordId: Long,
        msgType: Int,
        results: List<SenderDispatchResult>,
    ) {
        if (results.isEmpty()) return
        val now = System.currentTimeMillis()
        val dao = db.senderDispatchLogDao()
        results.forEach { result ->
            runCatching {
                dao.insert(
                    SenderDispatchLog(
                        recordId = recordId,
                        senderId = result.senderId,
                        senderType = result.senderType,
                        msgType = msgType,
                        success = result.success,
                        createdAt = now,
                    ),
                )
                AnalyticsTracker.logEvent(
                    "forward_result",
                    mapOf(
                        "sender_type" to result.senderType,
                        "msg_type" to msgType,
                        "success" to if (result.success) 1 else 0,
                    ),
                )
            }.onFailure { error ->
                XLog.w(
                    "Persist sender dispatch log failed: %s",
                    error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }
}
