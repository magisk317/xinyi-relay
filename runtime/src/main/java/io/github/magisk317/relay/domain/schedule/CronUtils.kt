package io.github.magisk317.relay.domain.schedule

import java.util.Calendar

data class SimpleWeeklySchedule(
    val weekdays: List<Int>,
    val hour: Int,
    val minute: Int,
) {
    val time: String
        get() = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
}

object CronUtils {
    /**
     * Parse a 5-field cron expression and return the next execution time.
     *
     * Supported syntax per field: "*", single value, comma list, range, and step.
     * Day-of-week uses 1-7 for Monday-Sunday. 0 is accepted as Sunday for cron compatibility.
     *
     * Examples:
     * - "0 10 * * *" -> Every day at 10:00
     * - "30 14 * * 1,3,5" -> Monday, Wednesday, Friday at 14:30
     * - "* * * * *" -> Every minute
     *
     * @throws IllegalArgumentException if cron expression is invalid
     */
    fun getNextRunTime(cron: String, fromTime: Long = System.currentTimeMillis()): Long {
        val expression = parseCronExpression(cron)

        val cal = Calendar.getInstance()
        cal.timeInMillis = fromTime
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MINUTE, 1)

        repeat(MAX_LOOKAHEAD_MINUTES) {
            if (expression.matches(cal)) {
                return cal.timeInMillis
            }
            cal.add(Calendar.MINUTE, 1)
        }

        throw IllegalArgumentException("Unable to resolve next run time for cron expression: $cron")
    }

    /**
     * Validate a cron expression without calculating next run time.
     * @return null if valid, error message if invalid
     */
    fun validateCronExpression(cron: String): String? {
        return try {
            getNextRunTime(cron)
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }
    }

    fun buildSimpleWeeklyCron(time: String, weekdays: List<Int>): String {
        val parts = time.trim().split(':')
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid time value: $time")
        }
        val hour = parts[0].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid hour value: ${parts[0]}")
        val minute = parts[1].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid minute value: ${parts[1]}")
        if (hour !in 0..23) {
            throw IllegalArgumentException("Invalid hour value: $hour (must be 0-23)")
        }
        if (minute !in 0..59) {
            throw IllegalArgumentException("Invalid minute value: $minute (must be 0-59)")
        }

        val safeWeekdays = normalizeWeekdays(weekdays)
        if (safeWeekdays.isEmpty()) {
            throw IllegalArgumentException("Weekdays cannot be blank")
        }
        val weekdayField = if (safeWeekdays == ALL_WEEKDAYS) {
            "*"
        } else {
            safeWeekdays.joinToString(",")
        }
        return "$minute $hour * * $weekdayField"
    }

    fun parseSimpleWeeklyCron(cron: String): SimpleWeeklySchedule? {
        val expression = runCatching { parseCronExpression(cron) }.getOrNull() ?: return null
        val minute = expression.minutes.singleOrNull() ?: return null
        val hour = expression.hours.singleOrNull() ?: return null
        if (expression.daysOfMonth != null || expression.months != null) return null
        return SimpleWeeklySchedule(
            weekdays = expression.weekdays ?: ALL_WEEKDAYS,
            hour = hour,
            minute = minute,
        )
    }

    private fun parseCronExpression(cron: String): CronExpression {
        if (cron.isBlank()) {
            throw IllegalArgumentException("Cron expression cannot be blank")
        }

        val parts = cron.trim().split(Regex("\\s+"))
        if (parts.size != CRON_FIELD_COUNT) {
            throw IllegalArgumentException(
                "Invalid cron expression: expected 5 fields (minute hour day month weekday), got ${parts.size}"
            )
        }

        return CronExpression(
            minutes = parseField(parts[0], 0, 59, "minute") ?: MINUTES,
            hours = parseField(parts[1], 0, 23, "hour") ?: HOURS,
            daysOfMonth = parseField(parts[2], 1, 31, "day-of-month", allowQuestion = true),
            months = parseField(parts[3], 1, 12, "month"),
            weekdays = parseField(parts[4], 0, 7, "day-of-week", allowQuestion = true)
                ?.map { if (it == 0) 7 else it }
                ?.distinct()
                ?.sorted(),
        )
    }

    private fun parseField(
        rawField: String,
        min: Int,
        max: Int,
        name: String,
        allowQuestion: Boolean = false,
    ): List<Int>? {
        val field = rawField.trim()
        if (field == "*" || (allowQuestion && field == "?")) return null
        if (field.isBlank()) {
            throw IllegalArgumentException("Invalid $name field: blank")
        }

        val values = linkedSetOf<Int>()
        field.split(',').forEach { rawPart ->
            val part = rawPart.trim()
            if (part.isBlank()) {
                throw IllegalArgumentException("Invalid $name field: $rawField")
            }

            val rangeAndStep = part.split('/', limit = 2)
            val rangePart = rangeAndStep[0]
            val step = if (rangeAndStep.size == 2) {
                rangeAndStep[1].toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException("Invalid $name step: ${rangeAndStep[1]}")
            } else {
                1
            }

            val (start, end) = when {
                rangePart == "*" -> min to max
                '-' in rangePart -> {
                    val bounds = rangePart.split('-', limit = 2)
                    if (bounds.size != 2) {
                        throw IllegalArgumentException("Invalid $name range: $rangePart")
                    }
                    val start = bounds[0].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid $name value: ${bounds[0]}")
                    val end = bounds[1].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid $name value: ${bounds[1]}")
                    start to end
                }

                else -> {
                    val value = rangePart.toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid $name value: $rangePart")
                    value to value
                }
            }

            if (start !in min..max || end !in min..max || start > end) {
                throw IllegalArgumentException("Invalid $name range: $rangePart (must be $min-$max)")
            }

            var value = start
            while (value <= end) {
                values += value
                value += step
            }
        }

        return values.sorted()
    }

    private fun CronExpression.matches(cal: Calendar): Boolean {
        val weekday = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
        return cal.get(Calendar.MINUTE) in minutes &&
            cal.get(Calendar.HOUR_OF_DAY) in hours &&
            (daysOfMonth == null || cal.get(Calendar.DAY_OF_MONTH) in daysOfMonth) &&
            (months == null || cal.get(Calendar.MONTH) + 1 in months) &&
            (weekdays == null || weekday in weekdays)
    }

    private fun normalizeWeekdays(weekdays: List<Int>): List<Int> {
        return weekdays
            .map { if (it == 0) 7 else it }
            .filter { it in ALL_WEEKDAYS }
            .distinct()
            .sorted()
    }

    private data class CronExpression(
        val minutes: List<Int>,
        val hours: List<Int>,
        val daysOfMonth: List<Int>?,
        val months: List<Int>?,
        val weekdays: List<Int>?,
    )

    private const val CRON_FIELD_COUNT = 5
    private const val MAX_LOOKAHEAD_MINUTES = 5 * 366 * 24 * 60
    private val MINUTES = (0..59).toList()
    private val HOURS = (0..23).toList()
    private val ALL_WEEKDAYS = (1..7).toList()
}
