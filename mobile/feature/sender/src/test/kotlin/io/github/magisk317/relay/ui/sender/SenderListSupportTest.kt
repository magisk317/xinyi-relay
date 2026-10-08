package io.github.magisk317.relay.ui.sender

import io.github.magisk317.relay.contract.constant.DispatchStrategy
import io.github.magisk317.relay.engine.model.Sender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SenderListSupportTest {

    @Test
    fun moveItem_reordersSenderList() {
        val senders = listOf(sender(10), sender(20), sender(30))

        val reordered = senders.moveItem(fromIndex = 0, toIndex = 2)

        assertEquals(listOf(20L, 30L, 10L), reordered.map { it.id })
    }

    @Test
    fun moveItem_ignoresInvalidIndexes() {
        val senders = listOf(sender(10), sender(20), sender(30))

        assertSame(senders, senders.moveItem(fromIndex = -1, toIndex = 1))
        assertSame(senders, senders.moveItem(fromIndex = 0, toIndex = 5))
        assertSame(senders, senders.moveItem(fromIndex = 1, toIndex = 1))
    }

    @Test
    fun priorityMapForOrder_usesCurrentListOrder() {
        val senders = listOf(sender(30), sender(10), sender(20))

        val priorities = priorityMapForOrder(senders)

        assertEquals(mapOf(30L to 0, 10L to 1, 20L to 2), priorities)
    }

    @Test
    fun reorderSenderToPriority_clampsPriorityIntoListBounds() {
        val senders = listOf(sender(10), sender(20), sender(30))

        val toStart = reorderSenderToPriority(senders, senderId = 30, priority = -5)
        val toEnd = reorderSenderToPriority(senders, senderId = 10, priority = 99)

        assertEquals(listOf(30L, 10L, 20L), toStart.map { it.id })
        assertEquals(listOf(20L, 30L, 10L), toEnd.map { it.id })
    }

    @Test
    fun reorderSenderToPriority_ignoresUnknownSender() {
        val senders = listOf(sender(10), sender(20), sender(30))

        assertSame(senders, reorderSenderToPriority(senders, senderId = 99, priority = 0))
    }

    @Test
    fun normalizeDispatchStrategy_keepsKnownValuesAndDefaultsUnknownValue() {
        assertEquals(
            DispatchStrategy.PRIMARY_ONLY,
            normalizeDispatchStrategy(DispatchStrategy.PRIMARY_ONLY),
        )
        assertEquals(
            DispatchStrategy.BROADCAST_ALL,
            normalizeDispatchStrategy(DispatchStrategy.BROADCAST_ALL),
        )
        assertEquals(
            DispatchStrategy.FAILOVER,
            normalizeDispatchStrategy(DispatchStrategy.FAILOVER),
        )
        assertEquals(DispatchStrategy.BROADCAST_ALL, normalizeDispatchStrategy(999))
    }

    private fun sender(id: Long): Sender = Sender(id = id)
}
