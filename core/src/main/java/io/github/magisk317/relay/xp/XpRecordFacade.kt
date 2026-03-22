package io.github.magisk317.relay.xp

import android.content.Context
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade

class XpRecordFacade(
    context: Context,
    private val delegate: RuntimeRecordFacade = RuntimeRecordFacade(context),
) {
    suspend fun isDuplicateSms(
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = delegate.isDuplicateSms(sender = sender, body = body, date = date, msgType = msgType)

    suspend fun findSmsRecordIdByFingerprint(
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Long? = delegate.findSmsRecordIdByFingerprint(sender = sender, body = body, date = date, msgType = msgType)

    suspend fun insertAutoInputAttempt(
        recordId: Long?,
        packageName: String?,
        codeLength: Int,
        attemptAt: Long = System.currentTimeMillis(),
    ): Long = delegate.insertAutoInputAttempt(
        recordId = recordId,
        packageName = packageName,
        codeLength = codeLength,
        attemptAt = attemptAt,
    )

    suspend fun updateAutoInputResult(
        attemptId: Long,
        success: Boolean,
        reason: String?,
    ) = delegate.updateAutoInputResult(attemptId = attemptId, success = success, reason = reason)

    suspend fun hasSmsDuplicateInRange(
        sender: String?,
        body: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = delegate.hasSmsDuplicateInRange(
        sender = sender,
        body = body,
        dateFrom = dateFrom,
        dateTo = dateTo,
        msgType = msgType,
    )

    suspend fun hasSmsCodeDuplicateByPackageInRange(
        smsCode: String?,
        packageName: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = delegate.hasSmsCodeDuplicateByPackageInRange(
        smsCode = smsCode,
        packageName = packageName,
        dateFrom = dateFrom,
        dateTo = dateTo,
        msgType = msgType,
    )

    suspend fun hasSmsCodeDuplicateByCompanyInRange(
        smsCode: String?,
        company: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = delegate.hasSmsCodeDuplicateByCompanyInRange(
        smsCode = smsCode,
        company = company,
        dateFrom = dateFrom,
        dateTo = dateTo,
        msgType = msgType,
    )

    suspend fun persistSmsForwardResult(
        smsMsg: SmsMsg,
        success: Boolean,
        target: String?,
        message: String,
        maxMessageLength: Int = 300,
    ) = delegate.persistSmsForwardResult(
        smsMsg = smsMsg,
        success = success,
        target = target,
        message = message,
        maxMessageLength = maxMessageLength,
    )

    suspend fun persistSmsHookDispatchFailure(
        smsMsg: SmsMsg,
        message: String,
        target: String = "SmsCode Engine",
        maxMessageLength: Int = 300,
    ) = delegate.persistSmsHookDispatchFailure(
        smsMsg = smsMsg,
        message = message,
        target = target,
        maxMessageLength = maxMessageLength,
    )

    suspend fun insertSmsRecord(
        smsMsg: SmsMsg,
        isCodeSms: Boolean,
    ): Long? = delegate.insertSmsRecord(smsMsg = smsMsg, isCodeSms = isCodeSms)
}
