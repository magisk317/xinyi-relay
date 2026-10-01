package io.github.magisk317.relay.contract.model

import io.github.magisk317.relay.contract.constant.MessageType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SenderActiveScheduleContractTest {

    @Test
    fun sanitizeRule_discards_invalid_ranges_and_disables_empty_rule() {
        val sanitized = SenderActiveScheduleEvaluator.sanitizeRule(
            SenderActiveScheduleRule(
                enabled = true,
                ranges = listOf(
                    SenderActiveScheduleRange("09:00", "09:00"),
                    SenderActiveScheduleRange("bad", "18:00"),
                ),
            ),
        )

        assertFalse(sanitized.enabled)
        assertTrue(sanitized.ranges.isEmpty())
    }

    @Test
    fun isAllowed_supports_overnight_whitelist_ranges() {
        val schedule = SenderActiveSchedule(
            appNotify = SenderActiveScheduleRule(
                enabled = true,
                mode = SenderActiveScheduleConst.MODE_WHITELIST,
                weekdays = SenderActiveScheduleConst.ALL_WEEKDAYS,
                ranges = listOf(SenderActiveScheduleRange("22:00", "02:00")),
            ),
        )

        assertTrue(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.APP_NOTIFY,
                LocalDateTime.of(2026, 7, 7, 23, 30),
            ),
        )
        assertFalse(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.APP_NOTIFY,
                LocalDateTime.of(2026, 7, 7, 15, 0),
            ),
        )
    }
}
