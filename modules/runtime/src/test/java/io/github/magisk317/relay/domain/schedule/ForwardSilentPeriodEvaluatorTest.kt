package io.github.magisk317.relay.domain.schedule

import io.github.magisk317.relay.contract.model.ForwardSilentPeriodConfig
import io.github.magisk317.relay.engine.schedule.ForwardSilentPeriodEvaluator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ForwardSilentPeriodEvaluatorTest {

    @Test
    fun isMuted_blocksInsideSameDayRange() {
        val config = ForwardSilentPeriodConfig(
            enabled = true,
            start = "09:00",
            end = "18:00",
            weekdays = listOf(1, 2, 3, 4, 5),
        )

        assertTrue(
            ForwardSilentPeriodEvaluator.isMuted(
                config,
                LocalDateTime.of(2026, 4, 27, 10, 0),
            ),
        )
        assertFalse(
            ForwardSilentPeriodEvaluator.isMuted(
                config,
                LocalDateTime.of(2026, 4, 27, 18, 0),
            ),
        )
    }

    @Test
    fun isMuted_usesStartDayForCrossMidnightRange() {
        val config = ForwardSilentPeriodConfig(
            enabled = true,
            start = "22:00",
            end = "08:00",
            weekdays = listOf(5),
        )

        assertTrue(
            ForwardSilentPeriodEvaluator.isMuted(
                config,
                LocalDateTime.of(2026, 5, 1, 23, 0),
            ),
        )
        assertTrue(
            ForwardSilentPeriodEvaluator.isMuted(
                config,
                LocalDateTime.of(2026, 5, 2, 1, 0),
            ),
        )
        assertFalse(
            ForwardSilentPeriodEvaluator.isMuted(
                config,
                LocalDateTime.of(2026, 5, 3, 1, 0),
            ),
        )
    }

    @Test
    fun sanitize_disablesInvalidOrEmptyRules() {
        val sanitized = ForwardSilentPeriodEvaluator.sanitize(
            ForwardSilentPeriodConfig(
                enabled = true,
                start = "bad",
                end = ForwardSilentPeriodConfig.DEFAULT_START,
                weekdays = listOf(9),
            ),
        )

        assertFalse(sanitized.enabled)
        assertEquals(ForwardSilentPeriodConfig.DEFAULT_START, sanitized.start)
        assertEquals(ForwardSilentPeriodConfig.DEFAULT_START, sanitized.end)
        assertEquals(ForwardSilentPeriodConfig.ALL_WEEKDAYS, sanitized.weekdays)
    }
}
