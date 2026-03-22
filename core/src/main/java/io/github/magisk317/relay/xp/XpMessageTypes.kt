package io.github.magisk317.relay.xp

import io.github.magisk317.relay.common.constant.MessageType

object XpMessageTypes {
    fun isSmsCode(messageType: MessageType?): Boolean = messageType == MessageType.SMS_CODE
}
