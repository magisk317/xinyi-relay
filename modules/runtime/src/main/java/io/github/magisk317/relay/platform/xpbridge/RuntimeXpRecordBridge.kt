package io.github.magisk317.relay.platform.xpbridge

import android.content.ContentValues
import android.content.Context
import io.github.magisk317.relay.android.data.db.DBProvider
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.contract.xpbridge.XpRecordRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpSmsBlacklistHitRecord
import io.github.magisk317.relay.contract.xpbridge.XpSmsRecord
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade
import io.github.magisk317.relay.platform.ipc.BlacklistHitBroadcast

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

    override suspend fun querySmsRecordsByCodeInRange(
        context: Context,
        smsCode: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int,
    ): List<XpSmsRecord> {
        return RuntimeRecordFacade(context).querySmsRecordsByCodeInRange(
            smsCode = smsCode,
            dateFrom = dateFrom,
            dateTo = dateTo,
            msgType = msgType,
        ).map(::toXpSmsRecord)
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
        deduplicate: Boolean,
    ): Long? {
        val record = smsMsg.toRuntimeSmsMsg()
        val inserted = context.contentResolver.insert(
            DBProvider.smsMsgContentUri(context),
            record.toContentValues(deduplicate),
        )
        return inserted?.lastPathSegment?.toLongOrNull()
    }

    override suspend fun insertSmsBlacklistHit(
        context: Context,
        hit: XpSmsBlacklistHitRecord,
    ): Long? {
        // SMS hooks run in the phone process (UID != app) and cannot open the relay app's private
        // Room database directly. Hand the hit to the app process over the forward broadcast IPC,
        // where ForwardReceiver persists it. Fire-and-forget: the row id is unknown to the sender.
        BlacklistHitBroadcast.dispatch(context, hit)
        return null
    }

    override suspend fun backfillSmsRouting(
        context: Context,
        sender: String?,
        body: String?,
        date: Long,
        simSlot: Int,
        subId: Int,
        msgType: Int,
        windowMs: Long,
    ): Boolean {
        return RuntimeRecordFacade(context).backfillSmsRouting(
            sender = sender,
            body = body,
            date = date,
            simSlot = simSlot,
            subId = subId,
            msgType = msgType,
            windowMs = windowMs,
        )
    }

    override fun exportCodeRecordToFile(context: Context, smsMsg: XpSmsRecord): Boolean {
        // Hook-side persistence is provider-only. External files are not an IPC transport.
        return false
    }

    private fun toXpSmsRecord(smsMsg: SmsMsg): XpSmsRecord {
        return XpSmsRecord(
            id = smsMsg.id,
            sender = smsMsg.sender,
            body = smsMsg.body,
            date = smsMsg.date,
            processedTime = smsMsg.processedTime,
            company = smsMsg.company,
            smsCode = smsMsg.smsCode,
            packageName = smsMsg.packageName,
            notifyChannelId = smsMsg.notifyChannelId,
            simSlot = smsMsg.simSlot,
            subId = smsMsg.subId,
            contactName = smsMsg.contactName,
            phoneArea = smsMsg.phoneArea,
            sessionKey = smsMsg.sessionKey,
            forwardStatus = smsMsg.forwardStatus,
            forwardTarget = smsMsg.forwardTarget,
            forwardMessage = smsMsg.forwardMessage,
            forwardTime = smsMsg.forwardTime,
            msgType = smsMsg.msgType,
            callType = smsMsg.callType,
        )
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

    private fun SmsMsg.toContentValues(deduplicate: Boolean): ContentValues = ContentValues().apply {
        put("sender", sender)
        put("body", body)
        put("date", date)
        put("processed_time", processedTime.takeIf { it > 0L } ?: System.currentTimeMillis())
        put("company", company)
        put("sms_code", smsCode)
        put("package_name", packageName)
        put("notify_channel_id", notifyChannelId)
        put("sim_slot", simSlot)
        put("sub_id", subId)
        put("contact_name", contactName)
        put("phone_area", phoneArea)
        put("msg_type", msgType)
        put("call_type", callType)
        put("session_key", sessionKey)
        put("forward_status", forwardStatus)
        put("forward_target", forwardTarget)
        put("forward_message", forwardMessage)
        put("forward_time", forwardTime)
        put("deduplicate", deduplicate)
    }
}
