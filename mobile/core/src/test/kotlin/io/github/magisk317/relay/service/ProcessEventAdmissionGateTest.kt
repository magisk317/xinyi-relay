package io.github.magisk317.relay.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProcessEventAdmissionGateTest {
    @Test
    fun tryEnter_allowsOnlyOneInFlightRequest() {
        val gate = ProcessEventAdmissionGate()

        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())

        gate.leave()

        assertTrue(gate.tryEnter())
    }
}
