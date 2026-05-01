package io.github.magisk317.relay.domain.system

import android.content.Context
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.entity.AutoInputEvent
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.data.datasource.PreferenceDataSourceImpl
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime-only persistence facade for lightweight hook / receiver writebacks.
 */
class RuntimeRecordFacade(
    context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
    relayRecordRepository: RelayRecordRepository? = null,
    private val recordInserter: (suspend (SmsMsg, Boolean) -> Long?)? = null,
) {
    private val appContext = context.applicationContext ?: context
    private val relayRecordRepository: RelayRecordRepository by lazy {
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
        recordInserter?.invoke(smsMsg, isCodeSms) ?: relayRecordRepository.insertRecord(
            smsMsg = smsMsg,
            isCodeSms = isCodeSms,
        )
    }

    private companion object {
        private const val SMS_HOOK_TARGET = "SmsCode Engine"
        private const val SMS_HOOK_MAX_MESSAGE_LENGTH = 300
    }
}
