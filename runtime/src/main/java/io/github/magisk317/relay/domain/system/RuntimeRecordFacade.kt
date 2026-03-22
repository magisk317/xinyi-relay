package io.github.magisk317.relay.domain.system

import android.content.Context
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.entity.AutoInputEvent
import io.github.magisk317.relay.data.db.entity.SmsMsg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime-only persistence facade for lightweight hook / receiver writebacks.
 */
class RuntimeRecordFacade(
    context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
) {
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
    ) = withContext(Dispatchers.IO) {
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

    suspend fun persistSmsForwardResult(
        smsMsg: SmsMsg,
        success: Boolean,
        target: String?,
        message: String,
        maxMessageLength: Int = 300,
    ) = withContext(Dispatchers.IO) {
        val dao = db.smsMsgDao()
        val timestamp = smsMsg.date.takeIf { it > 0L } ?: System.currentTimeMillis()
        val existing = dao.getByFingerprint(
            sender = smsMsg.sender,
            body = smsMsg.body,
            date = timestamp,
            msgType = smsMsg.msgType,
        )
        val updated = (existing ?: smsMsg.copy(date = timestamp)).copy(
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
}
