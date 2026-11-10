package io.github.magisk317.relay.feature.mode

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NonRootRuntimePropertyTest {
    @Test
    fun `arbitrary callback sequences follow the last connection event`() {
        val random = Random(601)
        for (enabled in listOf(false, true)) for (initial in listOf(false, true)) {
            val connection = ListenerConnection(initial)
            assertEquals(initial, connection.connected.value)
            repeat(1000) {
                val connected = random.nextBoolean()
                if (connected) connection.onConnected() else connection.onDisconnected()
                assertEquals(connected, connection.connected.value)
                assertEquals(enabled && connected, NonRootRuntimeRule.isRunning(enabled, connection.connected.value))
            }
        }
    }
}
