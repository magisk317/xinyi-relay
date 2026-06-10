package io.github.magisk317.relay.domain.system

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.entity.AutoInputEvent
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSourceImpl
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Runtime-only persistence facade for lightweight hook / receiver writebacks.
 */
class RuntimeRecordFacade(
    context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
    relayRecordRepository: MessageRecordRepository? = null,
    private val recordInserter: (suspend (SmsMsg, Boolean) -> Long?)? = null,
) {
    private val appContext = context.applicationContext ?: context
    private val relayRecordRepository: MessageRecordRepository by lazy {
        relayRecordRepository ?: RelayRecordRepository(
            context = appContext,
            db = db,
            preferenceDataSource = PreferenceDataSourceImpl(appContext),
        )
    }

    suspend fun isDuplicateSms(
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = withContext(Dispatchers.IO) {
        db.smsMsgDao().getByFingerprint(sender, body, date, msgType) != null
    }

    suspend fun findSmsRecordIdByFingerprint(
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Long? = withContext(Dispatchers.IO) {
        db.smsMsgDao().getByFingerprint(sender, body, date, msgType)?.id
    }

    suspend fun insertAutoInputAttempt(
        recordId: Long?,
        packageName: String?,
        codeLength: Int,
        attemptAt: Long = System.currentTimeMillis(),
    ): Long = withContext(Dispatchers.IO) {
        db.autoInputEventDao().insert(
            AutoInputEvent(
                recordId = recordId,
                packageName = packageName,
                codeLength = codeLength,
                attemptAt = attemptAt,
            ),
        )
    }

    suspend fun updateAutoInputResult(
        attemptId: Long,
        success: Boolean,
        reason: String?,
    ): Int = withContext(Dispatchers.IO) {
        db.autoInputEventDao().updateResult(attemptId, success, reason)
    }

    suspend fun hasSmsDuplicateInRange(
        sender: String?,
        body: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = withContext(Dispatchers.IO) {
        db.smsMsgDao().getByFingerprintInRange(sender, body, msgType, dateFrom, dateTo) != null
    }

    suspend fun hasSmsCodeDuplicateByPackageInRange(
        smsCode: String?,
        packageName: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = withContext(Dispatchers.IO) {
        db.smsMsgDao().getByCodeAndPackageInRange(smsCode, packageName, msgType, dateFrom, dateTo) != null
    }

    suspend fun hasSmsCodeDuplicateByCompanyInRange(
        smsCode: String?,
        company: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = withContext(Dispatchers.IO) {
        db.smsMsgDao().getByCodeAndCompanyInRange(smsCode, company, msgType, dateFrom, dateTo) != null
    }

    suspend fun querySmsRecordsByCodeInRange(
        smsCode: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): List<SmsMsg> = withContext(Dispatchers.IO) {
        db.smsMsgDao().getByCodeInRange(smsCode, msgType, dateFrom, dateTo)
    }

    suspend fun persistSmsForwardResult(
        smsMsg: SmsMsg,
        success: Boolean,
        target: String?,
        message: String,
        maxMessageLength: Int = 300,
    ) = withContext(Dispatchers.IO) {
        val dao = db.smsMsgDao()
        val timestamp = smsMsg.date.takeIf { it > 0L } ?: System.currentTimeMillis()
        val processedTime = smsMsg.processedTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val existing = dao.getByFingerprint(
            sender = smsMsg.sender,
            body = smsMsg.body,
            date = timestamp,
            msgType = smsMsg.msgType,
        )
        val updated = (existing ?: smsMsg.copy(date = timestamp, processedTime = processedTime)).copy(
            forwardStatus = if (success) SmsMsg.FORWARD_STATUS_SUCCESS else SmsMsg.FORWARD_STATUS_FAILED,
            forwardTarget = target,
            forwardMessage = message.take(maxMessageLength),
            forwardTime = System.currentTimeMillis(),
        )
        if (existing != null) {
            dao.update(updated)
        } else {
            dao.insert(updated)
        }
    }

    suspend fun persistSmsHookDispatchFailure(
        smsMsg: SmsMsg,
        message: String,
        target: String = SMS_HOOK_TARGET,
        maxMessageLength: Int = SMS_HOOK_MAX_MESSAGE_LENGTH,
    ) {
        persistSmsForwardResult(
            smsMsg = smsMsg,
            success = false,
            target = target,
            message = message,
            maxMessageLength = maxMessageLength,
        )
    }

    suspend fun insertSmsRecord(
        smsMsg: SmsMsg,
        isCodeSms: Boolean,
    ): Long? = withContext(Dispatchers.IO) {
        recordInserter?.invoke(smsMsg, isCodeSms) ?: (relayRecordRepository as RelayRecordRepository).insertRecord(
            smsMsg = smsMsg,
            isCodeSms = isCodeSms,
        )
    }

    suspend fun backfillSmsRouting(
        sender: String?,
        body: String?,
        date: Long,
        simSlot: Int,
        subId: Int,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
        windowMs: Long = ROUTING_BACKFILL_WINDOW_MS,
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedSimSlot = resolveBackfillSimSlot(simSlot, subId)
        val resolvedSubId = subId.takeIf { it > 0 } ?: 0
        if (resolvedSimSlot < 0 && resolvedSubId <= 0) {
            return@withContext false
        }

        val timestamp = date.takeIf { it > 0L } ?: System.currentTimeMillis()
        val candidates = db.smsMsgDao()
            .getAll()
            .asSequence()
            .filter { it.msgType == msgType }
            .map { it to abs(it.date - timestamp) }
            .filter { (_, deltaMs) -> deltaMs <= windowMs }
            .toList()
        val exactMatch = candidates
            .filter { (record, _) -> record.sender == sender && record.body == body }
            .minByOrNull { (_, deltaMs) -> deltaMs }
            ?.first
        val fallbackMatches = candidates
            .map { (record, _) -> record }
            .filter { it.simSlot < 0 || it.subId <= 0 }
        val existing = exactMatch ?: fallbackMatches.singleOrNull() ?: return@withContext false

        val newSimSlot = if (existing.simSlot < 0 && resolvedSimSlot >= 0) {
            resolvedSimSlot
        } else {
            existing.simSlot
        }
        val newSubId = if (existing.subId <= 0 && resolvedSubId > 0) {
            resolvedSubId
        } else {
            existing.subId
        }
        if (newSimSlot == existing.simSlot && newSubId == existing.subId) {
            return@withContext false
        }
        db.smsMsgDao().update(existing.copy(simSlot = newSimSlot, subId = newSubId))
        true
    }

    private fun resolveBackfillSimSlot(simSlot: Int, subId: Int): Int {
        if (simSlot >= 0) return simSlot
        if (subId <= 0) return -1
        val platformSlot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { SubscriptionManager.getSlotIndex(subId) }.getOrDefault(-1)
        } else {
            -1
        }
        if (platformSlot >= 0) return platformSlot
        return when (subId) {
            1 -> 0
            2 -> 1
            else -> -1
        }
    }

    private companion object {
        private const val SMS_HOOK_TARGET = "SmsCode Engine"
        private const val SMS_HOOK_MAX_MESSAGE_LENGTH = 300
        private const val ROUTING_BACKFILL_WINDOW_MS = 30 * 60 * 1000L
    }
}
