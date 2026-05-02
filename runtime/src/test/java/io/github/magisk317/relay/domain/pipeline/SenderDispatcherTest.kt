package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.contract.constant.DispatchStrategy
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.service.SenderDispatchResult
import io.github.magisk317.relay.engine.service.SenderDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Date

class SenderDispatcherTest {

    @Test
    fun dispatchToSenders_primaryOnly_usesHighestPrioritySender() = runBlocking {
        val dispatcher = FakeSenderDispatcher()

        val results = dispatcher.dispatchToSenders(
            senders = senders(),
            msgInfo = msgInfo(),
            strategy = DispatchStrategy.PRIMARY_ONLY,
        )

        assertEquals(listOf(2L), results.map { it.senderId })
        assertEquals(listOf(2L), dispatcher.attempts)
    }

    @Test
    fun dispatchToSenders_broadcastAll_attemptsEverySenderInPriorityOrder() = runBlocking {
        val dispatcher = FakeSenderDispatcher()

        val results = dispatcher.dispatchToSenders(
            senders = senders(),
            msgInfo = msgInfo(),
            strategy = DispatchStrategy.BROADCAST_ALL,
        )

        assertEquals(listOf(2L, 1L, 3L), results.map { it.senderId })
    }

    @Test
    fun dispatchToSenders_failover_stopsAfterFirstSuccess() = runBlocking {
        val dispatcher = FakeSenderDispatcher(successById = mapOf(2L to false, 1L to true, 3L to true))

        val results = dispatcher.dispatchToSenders(
            senders = senders(),
            msgInfo = msgInfo(),
            strategy = DispatchStrategy.FAILOVER,
        )

        assertEquals(listOf(2L, 1L), results.map { it.senderId })
        assertEquals(listOf(false, true), results.map { it.success })
        assertEquals(listOf(2L, 1L), dispatcher.attempts)
    }

    @Test
    fun dispatchToSenders_unknownStrategy_broadcastsAll() = runBlocking {
        val dispatcher = FakeSenderDispatcher()

        val results = dispatcher.dispatchToSenders(
            senders = senders(),
            msgInfo = msgInfo(),
            strategy = 99,
        )

        assertEquals(listOf(2L, 1L, 3L), results.map { it.senderId })
    }

    private fun senders(): List<Sender> = listOf(
        Sender(id = 1L, type = 10, name = "one", priority = 0),
        Sender(id = 2L, type = 10, name = "two", priority = 0),
        Sender(id = 3L, type = 10, name = "three", priority = 5),
    )

    private fun msgInfo(): MsgInfo = MsgInfo(
        from = "sender",
        content = "content",
        date = Date(0),
        simInfo = "",
    )

    private class FakeSenderDispatcher(
        private val successById: Map<Long, Boolean> = emptyMap(),
    ) : SenderDispatcher {
        val attempts = mutableListOf<Long>()

        override suspend fun dispatchToSender(
            sender: Sender,
            msgInfo: MsgInfo,
            traceId: String?,
        ): SenderDispatchResult {
            attempts += sender.id
            return SenderDispatchResult(
                senderId = sender.id,
                senderType = sender.type,
                senderName = sender.name,
                success = successById[sender.id] ?: true,
                message = "test",
            )
        }
    }
}
