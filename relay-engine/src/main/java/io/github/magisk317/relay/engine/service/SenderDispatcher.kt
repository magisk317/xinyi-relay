package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender

data class SenderDispatchResult(
    val senderId: Long,
    val senderType: Int,
    val senderName: String,
    val success: Boolean,
    val message: String,
)

interface SenderDispatcher {
    suspend fun dispatchToSender(sender: Sender, msgInfo: MsgInfo, traceId: String? = null): SenderDispatchResult

    suspend fun dispatchToSenders(senders: List<Sender>, msgInfo: MsgInfo, traceId: String? = null): List<SenderDispatchResult> {
        return senders.map { dispatchToSender(it, msgInfo, traceId) }
    }
}
