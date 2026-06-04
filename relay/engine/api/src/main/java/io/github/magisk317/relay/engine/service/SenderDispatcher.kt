package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.contract.constant.DispatchStrategy
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class SenderDispatchResult(
    val senderId: Long,
    val senderType: Int,
    val senderName: String,
    val success: Boolean,
    val message: String,
)

interface SenderDispatcher {
    suspend fun dispatchToSender(sender: Sender, msgInfo: MsgInfo, traceId: String? = null): SenderDispatchResult

    suspend fun dispatchToSenders(
        senders: List<Sender>,
        payloadProvider: suspend (Sender) -> MsgInfo,
        strategy: Int = DispatchStrategy.BROADCAST_ALL,
        traceId: String? = null,
    ): List<SenderDispatchResult> {
        val sorted = senders.sortedWith(compareBy<Sender> { it.priority }.thenByDescending { it.id })
        return when (strategy) {
            DispatchStrategy.PRIMARY_ONLY -> sorted.firstOrNull()?.let { sender ->
                listOf(dispatchToSender(sender, payloadProvider(sender), traceId))
            } ?: emptyList()

            DispatchStrategy.FAILOVER -> {
                val results = mutableListOf<SenderDispatchResult>()
                for (sender in sorted) {
                    val result = dispatchToSender(sender, payloadProvider(sender), traceId)
                    results += result
                    if (result.success) break
                }
                results
            }

            DispatchStrategy.BROADCAST_ALL -> dispatchToAll(sorted, payloadProvider, traceId)

            else -> dispatchToAll(sorted, payloadProvider, traceId)
        }
    }

    private suspend fun dispatchToAll(
        senders: List<Sender>,
        payloadProvider: suspend (Sender) -> MsgInfo,
        traceId: String?,
    ): List<SenderDispatchResult> = coroutineScope {
        senders.map { sender ->
            async { dispatchToSender(sender, payloadProvider(sender), traceId) }
        }.awaitAll()
    }
}
