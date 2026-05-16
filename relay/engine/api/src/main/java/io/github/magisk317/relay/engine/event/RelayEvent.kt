package io.github.magisk317.relay.engine.event

import io.github.magisk317.relay.contract.constant.MessageType

data class RelayEvent(
    val messageType: MessageType,
    val sourceType: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val packageName: String,
    val notifyChannelId: String,
    val companyOrAppName: String,
    val smsCode: String?,
    val callType: Int,
    val callStage: String,
    val simSlot: Int,
    val subId: Int,
    val contactName: String = "",
    val phoneArea: String = "",
    val targetSenderIds: List<Long>? = null,
) {
    fun isCallAlertStart(): Boolean {
        if (messageType != MessageType.CALL_NOTIFY) return false
        return callStage == "ringing" || callStage == "dialing" || callStage == "ongoing"
    }

    companion object {
        fun batteryReminder(
            messageType: MessageType = MessageType.APP_NOTIFY,
            title: String,
            content: String,
            timestamp: Long,
            packageName: String,
            senderId: Long,
            sourceType: String,
        ): RelayEvent {
            return RelayEvent(
                messageType = messageType,
                sourceType = sourceType,
                sender = title,
                body = content,
                timestamp = timestamp,
                packageName = packageName,
                notifyChannelId = "",
                companyOrAppName = "",
                smsCode = null,
                callType = 0,
                callStage = "",
                simSlot = -1,
                subId = 0,
                targetSenderIds = listOf(senderId),
            )
        }
    }
}
