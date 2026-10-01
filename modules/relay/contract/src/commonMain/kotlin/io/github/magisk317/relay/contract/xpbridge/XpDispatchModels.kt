package io.github.magisk317.relay.contract.xpbridge

enum class XpMessageType {
    SMS_CODE,
    SMS_PLAIN,
    APP_NOTIFY,
    CALL_NOTIFY,
}

object XpMessageTypes {
    fun isSmsCode(messageType: XpMessageType?): Boolean = messageType == XpMessageType.SMS_CODE
}

data class XpSmsHookDispatchResult(
    val dispatched: Boolean,
    val bypassUsed: Boolean,
    val tokenPresent: Boolean,
)

data class XpForwardPayload(
    val sender: String? = null,
    val body: String? = null,
    val date: Long = 0L,
    val company: String? = null,
    val smsCode: String? = null,
    val packageName: String? = null,
    val notifyChannelId: String = "",
    val msgType: String = MSG_TYPE_SMS,
    val forwardSource: String = "unknown",
    val eventId: String = "",
    val callType: Int = 0,
    val callStage: String = "",
    val simSlot: Int? = null,
    val subId: Int? = null,
) {
    companion object {
        const val MSG_TYPE_SMS = "sms"
        const val MSG_TYPE_APP_NOTIFY = "app_notify"
        const val MSG_TYPE_CALL_NOTIFY = "call_notify"
    }
}

data class XpPreparedSmsHookDispatch(
    val smsMsg: XpSmsRecord,
    val payload: XpForwardPayload,
    val messageType: XpMessageType? = null,
)
