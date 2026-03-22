package io.github.magisk317.relay.xp

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.platform.ipc.PreparedSmsHookDispatch as RuntimePreparedSmsHookDispatch

class PreparedXpSmsHookDispatch internal constructor(
    internal val runtimePrepared: RuntimePreparedSmsHookDispatch,
    val smsMsg: SmsMsg,
    val messageType: MessageType? = null,
    val simSlot: Int? = runtimePrepared.payload.simSlot,
    val subId: Int? = runtimePrepared.payload.subId,
)
