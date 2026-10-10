package io.github.magisk317.relay.engine.sender

import io.github.magisk317.relay.contract.constant.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.datetime.LocalDateTime

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
                LocalDateTime.parse("2026-04-27T10:00:00"),
            ),
        )
        assertTrue(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.SMS_CODE,
                LocalDateTime.parse("2026-04-27T19:00:00"),
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
                LocalDateTime.parse("2026-04-28T09:30:00"),
            ),
        )
        assertFalse(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.APP_NOTIFY,
                LocalDateTime.parse("2026-04-28T19:00:00"),
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

        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.parse("2026-04-27T11:00:00")))
        assertFalse(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.parse("2026-04-27T12:30:00")))
        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.parse("2026-04-27T14:00:00")))
    }

    @Test
    fun weekdayFiltering_respectsConfiguredDays() {
        val rule = SenderActiveScheduleRule(
            enabled = true,
            mode = SenderActiveScheduleConst.MODE_WHITELIST,
            weekdays = listOf(1, 2, 3, 4, 5),
            ranges = listOf(SenderActiveScheduleRange("09:00", "18:00")),
        )

        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.parse("2026-04-27T10:00:00")))
        assertFalse(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.parse("2026-05-03T10:00:00")))
    }

    @Test
    fun crossMidnightRange_usesStartDayWeekday() {
        val rule = SenderActiveScheduleRule(
            enabled = true,
            mode = SenderActiveScheduleConst.MODE_WHITELIST,
            weekdays = listOf(5),
            ranges = listOf(SenderActiveScheduleRange("22:00", "02:00")),
        )

        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.parse("2026-05-01T23:00:00")))
        assertTrue(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.parse("2026-05-02T01:00:00")))
        assertFalse(SenderActiveScheduleEvaluator.isRuleAllowed(rule, LocalDateTime.parse("2026-05-03T01:00:00")))
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
                LocalDateTime.parse("2026-04-27T08:30:00"),
            ),
        )
        assertFalse(
            SenderActiveScheduleEvaluator.isAllowed(
                schedule,
                MessageType.APP_NOTIFY,
                LocalDateTime.parse("2026-04-27T10:00:00"),
            ),
        )
    }
}
