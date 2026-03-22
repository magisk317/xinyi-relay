package io.github.magisk317.relay.xp

import io.github.magisk317.relay.platform.ipc.PreparedSmsHookDispatch as RuntimePreparedSmsHookDispatch

class PreparedXpSmsHookDispatch internal constructor(
    internal val runtimePrepared: RuntimePreparedSmsHookDispatch,
    val smsMsg: SmsMsg,
    val messageType: XpMessageType? = null,
    val simSlot: Int? = runtimePrepared.payload.simSlot,
    val subId: Int? = runtimePrepared.payload.subId,
)
