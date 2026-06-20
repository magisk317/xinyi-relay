package io.github.magisk317.relay.contract.xpbridge

import android.content.Context

interface XpRecordRuntimeBridge {
    suspend fun isDuplicateSms(
        context: Context,
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = XpSmsRecord.MSG_TYPE_SMS,
    ): Boolean

    suspend fun findSmsRecordIdByFingerprint(
        context: Context,
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = XpSmsRecord.MSG_TYPE_SMS,
    ): Long?

    suspend fun insertAutoInputAttempt(
        context: Context,
        recordId: Long?,
        packageName: String?,
        codeLength: Int,
        attemptAt: Long = System.currentTimeMillis(),
    ): Long

    suspend fun updateAutoInputResult(
        context: Context,
        attemptId: Long,
        success: Boolean,
        reason: String?,
    ): Int

    suspend fun hasSmsDuplicateInRange(
        context: Context,
        sender: String?,
        body: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = XpSmsRecord.MSG_TYPE_SMS,
    ): Boolean

    suspend fun hasSmsCodeDuplicateByPackageInRange(
        context: Context,
        smsCode: String?,
        packageName: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = XpSmsRecord.MSG_TYPE_SMS,
    ): Boolean

    suspend fun hasSmsCodeDuplicateByCompanyInRange(
        context: Context,
        smsCode: String?,
        company: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = XpSmsRecord.MSG_TYPE_SMS,
    ): Boolean

    suspend fun querySmsRecordsByCodeInRange(
        context: Context,
        smsCode: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = XpSmsRecord.MSG_TYPE_SMS,
    ): List<XpSmsRecord>

    suspend fun persistSmsForwardResult(
        context: Context,
        smsMsg: XpSmsRecord,
        success: Boolean,
        target: String?,
        message: String,
        maxMessageLength: Int = 300,
    )

    suspend fun persistSmsHookDispatchFailure(
        context: Context,
        smsMsg: XpSmsRecord,
        message: String,
        target: String = "SmsCode Engine",
        maxMessageLength: Int = 300,
    )

    suspend fun insertSmsRecord(
        context: Context,
        smsMsg: XpSmsRecord,
        isCodeSms: Boolean,
    ): Long?

    suspend fun insertSmsBlacklistHit(
        context: Context,
        hit: XpSmsBlacklistHitRecord,
    ): Long?

    suspend fun backfillSmsRouting(
        context: Context,
        sender: String?,
        body: String?,
        date: Long,
        simSlot: Int,
        subId: Int,
        msgType: Int = XpSmsRecord.MSG_TYPE_SMS,
        windowMs: Long = DEFAULT_ROUTING_BACKFILL_WINDOW_MS,
    ): Boolean

    fun exportCodeRecordToFile(context: Context, smsMsg: XpSmsRecord): Boolean

    companion object {
        const val DEFAULT_ROUTING_BACKFILL_WINDOW_MS = 30 * 60 * 1000L
    }
}

object NoopXpRecordRuntimeBridge : XpRecordRuntimeBridge {
    override suspend fun isDuplicateSms(
        context: Context,
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int,
    ): Boolean = false

    override suspend fun findSmsRecordIdByFingerprint(
        context: Context,
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int,
    ): Long? = null

    override suspend fun insertAutoInputAttempt(
        context: Context,
        recordId: Long?,
        packageName: String?,
        codeLength: Int,
        attemptAt: Long,
    ): Long = 0L

    override suspend fun updateAutoInputResult(
        context: Context,
        attemptId: Long,
        success: Boolean,
        reason: String?,
    ): Int = 0

    override suspend fun hasSmsDuplicateInRange(
        context: Context,
        sender: String?,
        body: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int,
    ): Boolean = false

    override suspend fun hasSmsCodeDuplicateByPackageInRange(
        context: Context,
        smsCode: String?,
        packageName: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int,
    ): Boolean = false

    override suspend fun hasSmsCodeDuplicateByCompanyInRange(
        context: Context,
        smsCode: String?,
        company: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int,
    ): Boolean = false

    override suspend fun querySmsRecordsByCodeInRange(
        context: Context,
        smsCode: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int,
    ): List<XpSmsRecord> = emptyList()

    override suspend fun persistSmsForwardResult(
        context: Context,
        smsMsg: XpSmsRecord,
        success: Boolean,
        target: String?,
        message: String,
        maxMessageLength: Int,
    ) = Unit

    override suspend fun persistSmsHookDispatchFailure(
        context: Context,
        smsMsg: XpSmsRecord,
        message: String,
        target: String,
        maxMessageLength: Int,
    ) = Unit

    override suspend fun insertSmsRecord(
        context: Context,
        smsMsg: XpSmsRecord,
        isCodeSms: Boolean,
    ): Long? = null

    override suspend fun insertSmsBlacklistHit(
        context: Context,
        hit: XpSmsBlacklistHitRecord,
    ): Long? = null

    override suspend fun backfillSmsRouting(
        context: Context,
        sender: String?,
        body: String?,
        date: Long,
        simSlot: Int,
        subId: Int,
        msgType: Int,
        windowMs: Long,
    ): Boolean = false

    override fun exportCodeRecordToFile(context: Context, smsMsg: XpSmsRecord): Boolean = false
}
