package io.github.magisk317.relay.matrix.e2ee

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StateTransitionsTest {
    private data class Fields(val first: Boolean = false, val second: Boolean = false)

    @Test
    fun concurrentReducersPreserveIndependentFields() = runBlocking {
        val state = MutableStateFlow(Fields())
        val firstWriters = (0 until 500).map {
            async(Dispatchers.Default) { state.updateWithTransition { it.copy(first = true) } }
        }
        val secondWriters = (0 until 500).map {
            async(Dispatchers.Default) { state.updateWithTransition { it.copy(second = true) } }
        }

        (firstWriters + secondWriters).awaitAll()

        assertEquals(Fields(first = true, second = true), state.value)
    }

    @Test
    fun transitionReportsTheCommittedPair() {
        val state = MutableStateFlow(Fields())

        val transition = state.updateWithTransition { it.copy(first = true) }

        assertEquals(Fields(), transition.before)
        assertEquals(Fields(first = true), transition.after)
        assertEquals(transition.after, state.value)
    }

    @Test
    fun overlappingReducersCommitBothChanges() = runBlocking {
        val state = MutableStateFlow(Fields())
        val bothReading = CyclicBarrier(2)
        val writers = listOf(true, false).map { firstField ->
            async(Dispatchers.Default) {
                var firstAttempt = true
                state.updateWithTransition { current ->
                    if (firstAttempt) {
                        firstAttempt = false
                        bothReading.await(10, TimeUnit.SECONDS)
                    }
                    if (firstField) current.copy(first = true) else current.copy(second = true)
                }
            }
        }

        val transitions = writers.awaitAll()

        assertEquals(Fields(first = true, second = true), state.value)
        assertEquals(1, transitions.count { it.before == Fields() })
        assertEquals(1, transitions.count { it.after == state.value })
    }

    @Test
    fun concurrentIncrementsReturnUniqueCommittedPairs() = runBlocking {
        val state = MutableStateFlow(0)
        val transitions = (0 until 500).map {
            async(Dispatchers.Default) { state.updateWithTransition { current -> current + 1 } }
        }.awaitAll()

        assertEquals(500, state.value)
        assertEquals((0 until 500).toSet(), transitions.map { it.before }.toSet())
        assertEquals((1..500).toSet(), transitions.map { it.after }.toSet())
        transitions.forEach { assertEquals(it.before + 1, it.after) }
    }
}
