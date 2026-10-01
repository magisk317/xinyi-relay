package io.github.magisk317.relay.engine.schedule

import io.github.magisk317.relay.contract.model.ForwardSilentPeriodConfig
import java.time.DayOfWeek
import java.time.LocalDateTime

object ForwardSilentPeriodEvaluator {
    private val timePattern = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

    fun sanitize(config: ForwardSilentPeriodConfig): ForwardSilentPeriodConfig {
        val start = config.start.trim().takeIf { parseMinutes(it) != null }
            ?: ForwardSilentPeriodConfig.DEFAULT_START
        val end = config.end.trim().takeIf { parseMinutes(it) != null }
            ?: ForwardSilentPeriodConfig.DEFAULT_END
        val weekdays = config.weekdays
            .filter { it in ForwardSilentPeriodConfig.ALL_WEEKDAYS }
            .distinct()
            .sorted()
            .ifEmpty { ForwardSilentPeriodConfig.ALL_WEEKDAYS }
        return ForwardSilentPeriodConfig(
            enabled = config.enabled && parseMinutes(start) != parseMinutes(end),
            start = start,
            end = end,
            weekdays = weekdays,
        )
    }

    fun isMuted(
        config: ForwardSilentPeriodConfig,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        val safeConfig = sanitize(config)
        if (!safeConfig.enabled) return false
        return matchesRange(safeConfig, now)
    }

    fun parseMinutes(time: String): Int? {
        val match = timePattern.matchEntire(time.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return hour * MINUTES_PER_HOUR + minute
    }

    private fun matchesRange(config: ForwardSilentPeriodConfig, now: LocalDateTime): Boolean {
        val startMinutes = parseMinutes(config.start) ?: return false
        val endMinutes = parseMinutes(config.end) ?: return false
        val weekday = now.dayOfWeek.value
        val previousWeekday = if (weekday == DayOfWeek.MONDAY.value) {
            DayOfWeek.SUNDAY.value
        } else {
            weekday - 1
        }
        val currentMinutes = now.hour * MINUTES_PER_HOUR + now.minute
        return if (startMinutes < endMinutes) {
            weekday in config.weekdays && currentMinutes in startMinutes until endMinutes
        } else {
            (weekday in config.weekdays && currentMinutes >= startMinutes) ||
                (previousWeekday in config.weekdays && currentMinutes < endMinutes)
        }
    }

    private const val MINUTES_PER_HOUR = 60
}
