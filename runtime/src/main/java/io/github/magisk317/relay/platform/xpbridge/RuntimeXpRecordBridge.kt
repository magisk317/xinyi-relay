package io.github.magisk317.relay.platform.xpbridge

import android.content.Context
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.contract.xpbridge.XpRecordRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpSmsRecord
import io.github.magisk317.relay.domain.system.RuntimeCodeRecordFileStore
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade

object RuntimeXpRecordBridge : XpRecordRuntimeBridge {
    override suspend fun isDuplicateSms(
        context: Context,
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int,
    ): Boolean {
        return RuntimeRecordFacade(context).isDuplicateSms(sender, body, date, msgType)
    }

    override suspend fun findSmsRecordIdByFingerprint(
        context: Context,
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int,
    ): Long? {
        return RuntimeRecordFacade(context).findSmsRecordIdByFingerprint(sender, body, date, msgType)
    }

    override suspend fun insertAutoInputAttempt(
        context: Context,
        recordId: Long?,
        packageName: String?,
        codeLength: Int,
        attemptAt: Long,
    ): Long {
        return RuntimeRecordFacade(context).insertAutoInputAttempt(recordId, packageName, codeLength, attemptAt)
    }

    override suspend fun updateAutoInputResult(
        context: Context,
        attemptId: Long,
        success: Boolean,
        reason: String?,
    ): Int {
        return RuntimeRecordFacade(context).updateAutoInputResult(attemptId, success, reason)
    }

    override suspend fun hasSmsDuplicateInRange(
        context: Context,
        sender: String?,
        body: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int,
    ): Boolean {
        return RuntimeRecordFacade(context).hasSmsDuplicateInRange(sender, body, dateFrom, dateTo, msgType)
    }

    override suspend fun hasSmsCodeDuplicateByPackageInRange(
        context: Context,
        smsCode: String?,
        packageName: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int,
    ): Boolean {
        return RuntimeRecordFacade(context).hasSmsCodeDuplicateByPackageInRange(
            smsCode = smsCode,
            packageName = packageName,
            dateFrom = dateFrom,
            dateTo = dateTo,
            msgType = msgType,
        )
    }

    override suspend fun hasSmsCodeDuplicateByCompanyInRange(
        context: Context,
        smsCode: String?,
        company: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int,
    ): Boolean {
        return RuntimeRecordFacade(context).hasSmsCodeDuplicateByCompanyInRange(
            smsCode = smsCode,
            company = company,
            dateFrom = dateFrom,
            dateTo = dateTo,
            msgType = msgType,
        )
    }

    override suspend fun persistSmsForwardResult(
        context: Context,
        smsMsg: XpSmsRecord,
        success: Boolean,
        target: String?,
        message: String,
        maxMessageLength: Int,
    ) {
        RuntimeRecordFacade(context).persistSmsForwardResult(
            smsMsg = smsMsg.toRuntimeSmsMsg(),
            success = success,
            target = target,
            message = message,
            maxMessageLength = maxMessageLength,
        )
    }

    override suspend fun persistSmsHookDispatchFailure(
        context: Context,
        smsMsg: XpSmsRecord,
        message: String,
        target: String,
        maxMessageLength: Int,
    ) {
        RuntimeRecordFacade(context).persistSmsHookDispatchFailure(
            smsMsg = smsMsg.toRuntimeSmsMsg(),
            message = message,
            target = target,
            maxMessageLength = maxMessageLength,
        )
    }

    override suspend fun insertSmsRecord(
        context: Context,
        smsMsg: XpSmsRecord,
        isCodeSms: Boolean,
    ): Long? {
        return RuntimeRecordFacade(context).insertSmsRecord(smsMsg.toRuntimeSmsMsg(), isCodeSms)
    }

    override fun exportCodeRecordToFile(context: Context, smsMsg: XpSmsRecord): Boolean {
        return RuntimeCodeRecordFileStore.exportToFile(context, smsMsg.toRuntimeSmsMsg())
    }

    private fun XpSmsRecord.toRuntimeSmsMsg(): SmsMsg {
        return SmsMsg(
            id = id,
            sender = sender,
            body = body,
            date = date,
            processedTime = processedTime,
            company = company,
            smsCode = smsCode,
            packageName = packageName,
            notifyChannelId = notifyChannelId,
            simSlot = simSlot,
            subId = subId,
            contactName = contactName,
            phoneArea = phoneArea,
            sessionKey = sessionKey,
            forwardStatus = forwardStatus,
            forwardTarget = forwardTarget,
            forwardMessage = forwardMessage,
            forwardTime = forwardTime,
            msgType = msgType,
            callType = callType,
        )
    }
}
