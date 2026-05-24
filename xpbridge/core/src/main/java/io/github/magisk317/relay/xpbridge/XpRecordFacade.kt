package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.xpbridge.NoopXpRecordRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpRecordRuntimeBridge

class XpRecordFacade(
    context: Context,
    private val bridge: XpRecordRuntimeBridge = runtimeBridge,
) {
    private val appContext = context.applicationContext ?: context

    suspend fun isDuplicateSms(
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = bridge.isDuplicateSms(
        context = appContext,
        sender = sender,
        body = body,
        date = date,
        msgType = msgType,
    )

    suspend fun findSmsRecordIdByFingerprint(
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Long? = bridge.findSmsRecordIdByFingerprint(
        context = appContext,
        sender = sender,
        body = body,
        date = date,
        msgType = msgType,
    )

    suspend fun insertAutoInputAttempt(
        recordId: Long?,
        packageName: String?,
        codeLength: Int,
        attemptAt: Long = System.currentTimeMillis(),
    ): Long = bridge.insertAutoInputAttempt(
        context = appContext,
        recordId = recordId,
        packageName = packageName,
        codeLength = codeLength,
        attemptAt = attemptAt,
    )

    suspend fun updateAutoInputResult(
        attemptId: Long,
        success: Boolean,
        reason: String?,
    ): Int = bridge.updateAutoInputResult(
        context = appContext,
        attemptId = attemptId,
        success = success,
        reason = reason,
    )

    suspend fun hasSmsDuplicateInRange(
        sender: String?,
        body: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = bridge.hasSmsDuplicateInRange(
        context = appContext,
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
    ): Boolean = bridge.hasSmsCodeDuplicateByPackageInRange(
        context = appContext,
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
    ): Boolean = bridge.hasSmsCodeDuplicateByCompanyInRange(
        context = appContext,
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
    ) = bridge.persistSmsForwardResult(
        context = appContext,
        smsMsg = smsMsg.toRecord(),
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
    ) = bridge.persistSmsHookDispatchFailure(
        context = appContext,
        smsMsg = smsMsg.toRecord(),
        message = message,
        target = target,
        maxMessageLength = maxMessageLength,
    )

    suspend fun insertSmsRecord(
        smsMsg: SmsMsg,
        isCodeSms: Boolean,
    ): Long? = bridge.insertSmsRecord(context = appContext, smsMsg = smsMsg.toRecord(), isCodeSms = isCodeSms)

    companion object {
        @Volatile
        private var runtimeBridge: XpRecordRuntimeBridge = NoopXpRecordRuntimeBridge

        internal val activeRuntimeBridge: XpRecordRuntimeBridge
            get() = runtimeBridge

        fun installRuntimeBridge(bridge: XpRecordRuntimeBridge?) {
            runtimeBridge = bridge ?: NoopXpRecordRuntimeBridge
        }
    }
}
