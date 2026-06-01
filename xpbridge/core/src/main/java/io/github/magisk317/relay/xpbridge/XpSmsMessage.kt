package io.github.magisk317.relay.xpbridge

import android.content.Intent
import android.os.Parcelable
import io.github.magisk317.relay.contract.xpbridge.XpSmsRecord
import io.github.magisk317.smscode.verification.SmsMessage
import kotlinx.parcelize.Parcelize

@Parcelize
data class XpSmsMessage(
    val id: Long = 0,
    override val sender: String? = null,
    override val body: String? = null,
    override val date: Long = 0,
    val processedTime: Long = 0L,
    override val company: String? = null,
    override val smsCode: String? = null,
    override val packageName: String? = null,
    val notifyChannelId: String = "",
    val simSlot: Int = -1,
    val subId: Int = 0,
    val contactName: String = "",
    val phoneArea: String = "",
    val sessionKey: String = "",
    var forwardStatus: Int = FORWARD_STATUS_NONE,
    var forwardTarget: String? = null,
    var forwardMessage: String? = null,
    var forwardTime: Long = 0L,
    val msgType: Int = MSG_TYPE_SMS,
    val callType: Int = 0,
) : Parcelable, SmsMessage {
    fun toRecord(): XpSmsRecord {
        return XpSmsRecord(
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

    companion object {
        const val FORWARD_STATUS_NONE = XpSmsRecord.FORWARD_STATUS_NONE
        const val FORWARD_STATUS_SUCCESS = XpSmsRecord.FORWARD_STATUS_SUCCESS
        const val FORWARD_STATUS_FAILED = XpSmsRecord.FORWARD_STATUS_FAILED
        const val FORWARD_STATUS_PARTIAL = XpSmsRecord.FORWARD_STATUS_PARTIAL
        const val FORWARD_STATUS_BLOCKED = XpSmsRecord.FORWARD_STATUS_BLOCKED

        const val MSG_TYPE_SMS = XpSmsRecord.MSG_TYPE_SMS
        const val MSG_TYPE_APP_NOTIFY = XpSmsRecord.MSG_TYPE_APP_NOTIFY
        const val MSG_TYPE_CALL_NOTIFY = XpSmsRecord.MSG_TYPE_CALL_NOTIFY

        @JvmStatic
        fun fromIntent(intent: Intent): XpSmsMessage {
            return XpDispatchCoordinator.parseIncomingSms(intent) ?: XpSmsMessage()
        }

        @JvmStatic
        fun fromRecord(smsMsg: XpSmsRecord): XpSmsMessage {
            return XpSmsMessage(
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
    }
}
