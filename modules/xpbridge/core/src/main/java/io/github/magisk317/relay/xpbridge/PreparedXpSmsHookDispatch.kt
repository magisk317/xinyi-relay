package io.github.magisk317.relay.xpbridge

import io.github.magisk317.relay.contract.xpbridge.XpPreparedSmsHookDispatch

class PreparedXpSmsHookDispatch internal constructor(
    internal val prepared: XpPreparedSmsHookDispatch,
    val smsMsg: SmsMsg = SmsMsg.fromRecord(prepared.smsMsg),
    val messageType: XpMessageType? = prepared.messageType,
    val simSlot: Int? = prepared.payload.simSlot,
    val subId: Int? = prepared.payload.subId,
)
