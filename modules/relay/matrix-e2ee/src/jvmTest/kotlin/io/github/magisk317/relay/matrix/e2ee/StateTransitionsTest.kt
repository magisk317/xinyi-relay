package io.github.magisk317.relay.matrix.e2ee

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
}
