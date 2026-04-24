package io.github.magisk317.relay.domain.sender

import io.github.magisk317.relay.common.constant.MessageType
import java.io.Serializable
import java.time.DayOfWeek
import java.time.LocalDateTime

data class SenderActiveSchedule(
    val sms: SenderActiveScheduleRule = SenderActiveScheduleRule(),
    val appNotify: SenderActiveScheduleRule = SenderActiveScheduleRule(),
    val callNotify: SenderActiveScheduleRule = SenderActiveScheduleRule(),
) : Serializable

data class SenderActiveScheduleRule(
    val enabled: Boolean = false,
    val mode: String = SenderActiveScheduleConst.MODE_BLACKLIST,
    val weekdays: List<Int> = SenderActiveScheduleConst.ALL_WEEKDAYS,
    val ranges: List<SenderActiveScheduleRange> = emptyList(),
) : Serializable

data class SenderActiveScheduleRange(
    val start: String = "",
    val end: String = "",
) : Serializable

data class SenderActiveScheduleSummary(
    val smsRanges: Int,
    val appNotifyRanges: Int,
    val callNotifyRanges: Int,
)

object SenderActiveScheduleConst {
    const val MODE_BLACKLIST = "blacklist"
    const val MODE_WHITELIST = "whitelist"
    val ALL_WEEKDAYS: List<Int> = (DayOfWeek.MONDAY.value..DayOfWeek.SUNDAY.value).toList()
    private val TIME_PATTERN = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

    fun parseMinutes(time: String): Int? {
        val match = TIME_PATTERN.matchEntire(time.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return hour * 60 + minute
    }
}

object SenderActiveScheduleEvaluator {
    fun sanitize(schedule: SenderActiveSchedule?): SenderActiveSchedule {
        val safeSchedule = schedule ?: SenderActiveSchedule()
        return SenderActiveSchedule(
            sms = sanitizeRule(safeSchedule.sms),
            appNotify = sanitizeRule(safeSchedule.appNotify),
            callNotify = sanitizeRule(safeSchedule.callNotify),
        )
    }

    fun sanitizeRule(rule: SenderActiveScheduleRule?): SenderActiveScheduleRule {
        val safeRule = rule ?: SenderActiveScheduleRule()
        val mode = if (safeRule.mode == SenderActiveScheduleConst.MODE_WHITELIST) {
            SenderActiveScheduleConst.MODE_WHITELIST
        } else {
            SenderActiveScheduleConst.MODE_BLACKLIST
        }
        val weekdays = safeRule.weekdays
            .filter { it in SenderActiveScheduleConst.ALL_WEEKDAYS }
            .distinct()
            .ifEmpty { SenderActiveScheduleConst.ALL_WEEKDAYS }
        val ranges = safeRule.ranges.mapNotNull { range ->
            val start = range.start.trim()
            val end = range.end.trim()
            val startMinutes = SenderActiveScheduleConst.parseMinutes(start) ?: return@mapNotNull null
            val endMinutes = SenderActiveScheduleConst.parseMinutes(end) ?: return@mapNotNull null
            if (startMinutes == endMinutes) return@mapNotNull null
            SenderActiveScheduleRange(start = start, end = end)
        }
        return SenderActiveScheduleRule(
            enabled = safeRule.enabled && ranges.isNotEmpty(),
            mode = mode,
            weekdays = weekdays,
            ranges = ranges,
        )
    }

    fun summarize(schedule: SenderActiveSchedule?): SenderActiveScheduleSummary {
        val safeSchedule = sanitize(schedule)
        return SenderActiveScheduleSummary(
            smsRanges = countEnabledRanges(safeSchedule.sms),
            appNotifyRanges = countEnabledRanges(safeSchedule.appNotify),
            callNotifyRanges = countEnabledRanges(safeSchedule.callNotify),
        )
    }

    fun isAllowed(
        schedule: SenderActiveSchedule?,
        messageType: MessageType,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        val safeSchedule = sanitize(schedule)
        val rule = when (messageType) {
            MessageType.SMS_CODE,
            MessageType.SMS_PLAIN,
            -> safeSchedule.sms

            MessageType.APP_NOTIFY -> safeSchedule.appNotify
            MessageType.CALL_NOTIFY -> safeSchedule.callNotify
        }
        return isRuleAllowed(rule, now)
    }

    fun isRuleAllowed(
        rule: SenderActiveScheduleRule,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        if (!rule.enabled) return true
        val matched = matchesAnyRange(rule, now)
        return when (rule.mode) {
            SenderActiveScheduleConst.MODE_WHITELIST -> matched
            else -> !matched
        }
    }

    private fun matchesAnyRange(rule: SenderActiveScheduleRule, now: LocalDateTime): Boolean {
        val weekday = now.dayOfWeek.value
        val previousWeekday = if (weekday == DayOfWeek.MONDAY.value) {
            DayOfWeek.SUNDAY.value
        } else {
            weekday - 1
        }
        val currentMinutes = now.hour * 60 + now.minute
        return rule.ranges.any { range ->
            val startMinutes = SenderActiveScheduleConst.parseMinutes(range.start) ?: return@any false
            val endMinutes = SenderActiveScheduleConst.parseMinutes(range.end) ?: return@any false
            when {
                startMinutes < endMinutes -> {
                    weekday in rule.weekdays && currentMinutes in startMinutes until endMinutes
                }
                else -> {
                    (weekday in rule.weekdays && currentMinutes >= startMinutes) ||
                        (previousWeekday in rule.weekdays && currentMinutes < endMinutes)
                }
            }
        }
    }

    private fun countEnabledRanges(rule: SenderActiveScheduleRule): Int {
        return if (rule.enabled) rule.ranges.size else 0
    }
}
