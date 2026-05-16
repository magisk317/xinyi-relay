package io.github.magisk317.relay.domain.schedule

import io.github.magisk317.relay.engine.schedule.CronUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.Calendar

class CronUtilsTest {
    @Test
    fun `returns next daily run for fixed minute and hour`() {
        val from = timeOf(hour = 9, minute = 30)
        val next = CronUtils.getNextRunTime("0 10 * * *", from)

        assertTime(hour = 10, minute = 0, timeMillis = next)
    }

    @Test
    fun `fixed daily time moves to tomorrow when current minute already passed`() {
        val from = timeOf(day = 1, hour = 10, minute = 0)
        val next = CronUtils.getNextRunTime("0 10 * * *", from)

        assertTime(day = 2, hour = 10, minute = 0, timeMillis = next)
    }

    @Test
    fun `wildcard minute restricted to an hour starts at the top of the target hour`() {
        val from = timeOf(hour = 9, minute = 30)
        val next = CronUtils.getNextRunTime("* 10 * * *", from)

        assertTime(hour = 10, minute = 0, timeMillis = next)
    }

    @Test
    fun `wildcard minute restricted to an hour does not spill into the next hour`() {
        val from = timeOf(day = 1, hour = 10, minute = 59, second = 30)
        val next = CronUtils.getNextRunTime("* 10 * * *", from)

        assertTime(day = 2, hour = 10, minute = 0, timeMillis = next)
    }

    @Test
    fun `weekday field schedules next matching weekday`() {
        val from = timeOf(day = 1, hour = 9, minute = 30)
        val next = CronUtils.getNextRunTime("0 10 * * 2", from)

        assertTime(day = 6, hour = 10, minute = 0, timeMillis = next)
    }

    @Test
    fun `simple weekly cron round trips`() {
        val cron = CronUtils.buildSimpleWeeklyCron("09:30", listOf(1, 3, 5))
        val simple = CronUtils.parseSimpleWeeklyCron(cron)

        assertEquals("30 9 * * 1,3,5", cron)
        assertEquals(listOf(1, 3, 5), simple?.weekdays)
        assertEquals("09:30", simple?.time)
        assertNull(CronUtils.validateCronExpression(cron))
    }

    @Test
    fun `invalid cron fields are rejected`() {
        assertNotNull(CronUtils.validateCronExpression("61 10 * * *"))
    }

    private fun timeOf(
        day: Int = 1,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun assertTime(
        day: Int = 1,
        hour: Int,
        minute: Int,
        timeMillis: Long,
    ) {
        Calendar.getInstance().apply {
            timeInMillis = timeMillis
            assertEquals(2026, get(Calendar.YEAR))
            assertEquals(Calendar.JANUARY, get(Calendar.MONTH))
            assertEquals(day, get(Calendar.DAY_OF_MONTH))
            assertEquals(hour, get(Calendar.HOUR_OF_DAY))
            assertEquals(minute, get(Calendar.MINUTE))
            assertEquals(0, get(Calendar.SECOND))
            assertEquals(0, get(Calendar.MILLISECOND))
        }
    }
}
