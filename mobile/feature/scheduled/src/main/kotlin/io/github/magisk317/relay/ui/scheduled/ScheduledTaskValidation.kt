package io.github.magisk317.relay.ui.scheduled

import io.github.magisk317.relay.engine.schedule.CronUtils
import io.github.magisk317.relay.engine.model.ScheduledTask

/** Validation problem kinds, resolved to localized strings by the ViewModel. */
enum class ScheduledTaskProblem {
    SMS_CHANNEL_DISABLED,
    CRON_INVALID,
    SIM_SLOT_INVALID,
    CONTENT_BLANK,
    MOBILES_BLANK,
    MOBILE_INVALID,
}

data class ScheduledTaskProblemReport(val kind: ScheduledTaskProblem, val invalidTarget: String? = null)

/** Pure validation for scheduled SMS tasks; string resolution stays in the ViewModel. */
object ScheduledTaskValidation {
    private val TARGET_SEPARATOR = "[,，;；]".toRegex()
    private val MOBILE_PATTERN = Regex("^\\+?[0-9][0-9\\-\\s]{2,}$")

    fun validate(
        taskType: String,
        smsChannelEnabled: Boolean,
        cronExpression: String,
        simSlot: Int,
        content: String,
        mobiles: String,
        cronValidator: (String) -> String? = CronUtils::validateCronExpression,
    ): ScheduledTaskProblemReport? {
        if (taskType == ScheduledTask.TASK_TYPE_SMS && !smsChannelEnabled) {
            return ScheduledTaskProblemReport(ScheduledTaskProblem.SMS_CHANNEL_DISABLED)
        }
        cronValidator(cronExpression)?.let {
            return ScheduledTaskProblemReport(ScheduledTaskProblem.CRON_INVALID)
        }
        if (simSlot !in 0..2) {
            return ScheduledTaskProblemReport(ScheduledTaskProblem.SIM_SLOT_INVALID)
        }
        if (content.isBlank()) {
            return ScheduledTaskProblemReport(ScheduledTaskProblem.CONTENT_BLANK)
        }

        val targets = mobiles
            .replace(TARGET_SEPARATOR, ",")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (targets.isEmpty()) {
            return ScheduledTaskProblemReport(ScheduledTaskProblem.MOBILES_BLANK)
        }

        val invalidTarget = targets.firstOrNull { target -> !MOBILE_PATTERN.matches(target) }
        if (invalidTarget != null) {
            return ScheduledTaskProblemReport(ScheduledTaskProblem.MOBILE_INVALID, invalidTarget)
        }

        return null
    }
}
