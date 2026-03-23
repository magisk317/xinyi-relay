package io.github.magisk317.relay.xpbridge

object XpMessageTypes {
    fun isSmsCode(messageType: XpMessageType?): Boolean = messageType == XpMessageType.SMS_CODE
}
