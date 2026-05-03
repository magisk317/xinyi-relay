package io.github.magisk317.relay.domain.schedule

import java.util.Calendar

object CronUtils {
    /**
     * Parse a basic cron expression (e.g. "0 10 * * *") and return the next execution time.
     * Supports only 5 fields: minute, hour, day of month, month, day of week.
     * Only basic '*' and numbers are supported for simplicity, or complex libraries like Quartz can be used if needed.
     * For now, returning next day at 00:00 as placeholder for missing impl.
     */
    fun getNextRunTime(cron: String, fromTime: Long = System.currentTimeMillis()): Long {
        if (cron.isBlank()) return fromTime + 24 * 3600 * 1000L

        // Simple fallback parsing for demo. In real production, Quartz/cron-parser should be used.
        // E.g. "0 10 * * *" -> every day at 10:00
        val parts = cron.split(" ")
        if (parts.size >= 5) {
            val minuteStr = parts[0]
            val hourStr = parts[1]
            val cal = Calendar.getInstance()
            cal.timeInMillis = fromTime

            val minute = minuteStr.toIntOrNull() ?: 0
            val hour = hourStr.toIntOrNull() ?: 0

            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            if (cal.timeInMillis <= fromTime) {
                cal.add(Calendar.DAY_OF_YEAR, 1) // next day
            }
            return cal.timeInMillis
        }
        return fromTime + 24 * 3600 * 1000L
    }
}
