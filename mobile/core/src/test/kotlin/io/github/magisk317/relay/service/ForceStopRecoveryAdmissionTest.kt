package io.github.magisk317.relay.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForceStopRecoveryAdmissionTest {
    @Test
    fun rejectedTokenDoesNotConsumeThrottlePermit() {
        var now = 1_000L
        val throttle = ProcessEventThrottle(minIntervalMillis = 5_000L) { now }

        assertEquals(RecoveryAdmission.TOKEN_REJECTED, recoveryAdmission(false, throttle))
        assertEquals(RecoveryAdmission.ADMITTED, recoveryAdmission(true, throttle))
        assertEquals(RecoveryAdmission.THROTTLED, recoveryAdmission(true, throttle))

        now += 5_000L
        assertEquals(RecoveryAdmission.ADMITTED, recoveryAdmission(true, throttle))
    }
}
