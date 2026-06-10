package io.github.magisk317.relay.engine.sender

import io.github.magisk317.relay.contract.constant.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SenderActiveScheduleEvaluatorTest {

    @Test
    fun blacklist_blocksInsideMatchedRange() {
        val schedule = SenderActiveSchedule(
            sms = SenderActiveScheduleRule(
                enabled = true,
                mode = SenderActiveScheduleConst.MODE_BLACKLIST,
                weekdays = listOf(1, 2, 3, 4, 5),
                ranges = listOf(SenderActiveScheduleRange("09:00", "18:00")),
            ),
        )

        assertFalse(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.SMS_CODE,
                LocalDateTime.of(2026, 4, 27, 10, 0),
            ),
        )
        assertTrue(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.SMS_CODE,
                LocalDateTime.of(2026, 4, 27, 19, 0),
            ),
        )
    }

    @Test
    fun whitelist_blocksOutsideMatchedRange() {
        val schedule = SenderActiveSchedule(
            appNotify = SenderActiveScheduleRule(
                enabled = true,
                mode = SenderActiveScheduleConst.MODE_WHITELIST,
                weekdays = listOf(1, 2, 3, 4, 5),
                ranges = listOf(SenderActiveScheduleRange("09:00", "18:00")),
            ),
        )

        assertTrue(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.APP_NOTIFY,
                LocalDateTime.of(2026, 4, 28, 9, 30),
            ),
        )
        assertFalse(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.APP_NOTIFY,
                LocalDateTime.of(2026, 4, 28, 19, 0),
            ),
        )
    }

    @Test
    fun multipleRanges_areOrMatched() {
        val rule = SenderActiveScheduleRule(
            enabled = true,
            mode = SenderActiveScheduleConst.MODE_WHITELIST,
            weekdays = SenderActiveScheduleConst.ALL_WEEKDAYS,
            ranges = listOf(
                SenderActiveScheduleRange("09:00", "12:00"),
                SenderActiveScheduleRange("13:30", "18:00"),
            ),
        )

        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.of(2026, 4, 27, 11, 0)))
        assertFalse(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.of(2026, 4, 27, 12, 30)))
        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.of(2026, 4, 27, 14, 0)))
    }

    @Test
    fun weekdayFiltering_respectsConfiguredDays() {
        val rule = SenderActiveScheduleRule(
            enabled = true,
            mode = SenderActiveScheduleConst.MODE_WHITELIST,
            weekdays = listOf(1, 2, 3, 4, 5),
            ranges = listOf(SenderActiveScheduleRange("09:00", "18:00")),
        )

        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.of(2026, 4, 27, 10, 0)))
        assertFalse(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.of(2026, 5, 3, 10, 0)))
    }

    @Test
    fun crossMidnightRange_usesStartDayWeekday() {
        val rule = SenderActiveScheduleRule(
            enabled = true,
            mode = SenderActiveScheduleConst.MODE_WHITELIST,
            weekdays = listOf(5),
            ranges = listOf(SenderActiveScheduleRange("22:00", "02:00")),
        )

        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.of(2026, 5, 1, 23, 0)))
        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.of(2026, 5, 2, 1, 0)))
        assertFalse(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.of(2026, 5, 3, 1, 0)))
    }

    @Test
    fun invalidRules_sanitizeToDisabled() {
        val sanitized = SenderActiveScheduleEvaluator.sanitizeRule(
            SenderActiveScheduleRule(
                enabled = true,
                mode = "unknown",
                weekdays = listOf(9),
                ranges = listOf(
                    SenderActiveScheduleRange("09:00", "09:00"),
                    SenderActiveScheduleRange("bad", "18:00"),
                ),
            ),
        )

        assertFalse(sanitized.enabled)
        assertEquals(SenderActiveScheduleConst.MODE_BLACKLIST, sanitized.mode)
        assertEquals(SenderActiveScheduleConst.ALL_WEEKDAYS, sanitized.weekdays)
        assertTrue(sanitized.ranges.isEmpty())
    }

    @Test
    fun customIngressSharesAppNotifyBucket() {
        val schedule = SenderActiveSchedule(
            appNotify = SenderActiveScheduleRule(
                enabled = true,
                mode = SenderActiveScheduleConst.MODE_WHITELIST,
                weekdays = SenderActiveScheduleConst.ALL_WEEKDAYS,
                ranges = listOf(SenderActiveScheduleRange("08:00", "09:00")),
            ),
        )

        assertTrue(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.APP_NOTIFY,
                LocalDateTime.of(2026, 4, 27, 8, 30),
            ),
        )
        assertFalse(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.APP_NOTIFY,
                LocalDateTime.of(2026, 4, 27, 10, 0),
            ),
        )
    }
}
