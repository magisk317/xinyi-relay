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
