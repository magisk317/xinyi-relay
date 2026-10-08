package io.github.magisk317.relay.ui.record

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordPageActivationTest {

    @Test
    fun activePageFlow_subscribesOnlyWhileActive_andRetainsLastSnapshot() = runBlocking {
        val active = MutableStateFlow(false)
        val sourceValues = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val subscriptions = Channel<Unit>(Channel.UNLIMITED)
        val cancellations = Channel<Unit>(Channel.UNLIMITED)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val retained = activePageFlow(active) {
            flow {
                subscriptions.send(Unit)
                try {
                    emitAll(sourceValues)
                } finally {
                    cancellations.trySend(Unit)
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, 0)

        try {
            assertTrue(subscriptions.tryReceive().isFailure)

            active.value = true
            withTimeout(1_000L) { subscriptions.receive() }
            sourceValues.emit(1)
            withTimeout(1_000L) { retained.first { it == 1 } }

            active.value = false
            withTimeout(1_000L) { cancellations.receive() }
            sourceValues.emit(2)
            assertEquals(1, retained.value)

            active.value = true
            withTimeout(1_000L) { subscriptions.receive() }
            sourceValues.emit(3)
            withTimeout(1_000L) { retained.first { it == 3 } }
        } finally {
            scope.cancel()
        }
    }
}
