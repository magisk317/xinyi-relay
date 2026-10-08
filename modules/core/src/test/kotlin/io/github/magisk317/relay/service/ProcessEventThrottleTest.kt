package io.github.magisk317.relay.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProcessEventThrottleTest {
    @Test
    fun tryAcquire_allowsOneEventPerInterval() {
        var now = 1_000L
        val throttle = ProcessEventThrottle(minIntervalMillis = 5_000L) { now }

        assertTrue(throttle.tryAcquire())
        assertFalse(throttle.tryAcquire())
        now = 5_999L
        assertFalse(throttle.tryAcquire())
        now = 6_000L
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun tryAcquire_failsClosedWhenClockMovesBackwards() {
        var now = 10_000L
        val throttle = ProcessEventThrottle(minIntervalMillis = 5_000L) { now }

        assertTrue(throttle.tryAcquire())
        now = 9_999L
        assertFalse(throttle.tryAcquire())
    }
}
