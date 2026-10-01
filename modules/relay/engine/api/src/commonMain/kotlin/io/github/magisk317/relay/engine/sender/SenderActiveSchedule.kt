package io.github.magisk317.relay.engine.sender

import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.contract.model.SenderActiveSchedule as ContractSenderActiveSchedule
import io.github.magisk317.relay.contract.model.SenderActiveScheduleConst as ContractSenderActiveScheduleConst
import io.github.magisk317.relay.contract.model.SenderActiveScheduleEvaluator as ContractSenderActiveScheduleEvaluator
import io.github.magisk317.relay.contract.model.SenderActiveScheduleRange as ContractSenderActiveScheduleRange
import io.github.magisk317.relay.contract.model.SenderActiveScheduleRule as ContractSenderActiveScheduleRule
import io.github.magisk317.relay.contract.model.SenderActiveScheduleSummary as ContractSenderActiveScheduleSummary
import java.time.LocalDateTime

typealias SenderActiveSchedule = ContractSenderActiveSchedule
typealias SenderActiveScheduleRule = ContractSenderActiveScheduleRule
typealias SenderActiveScheduleRange = ContractSenderActiveScheduleRange
typealias SenderActiveScheduleSummary = ContractSenderActiveScheduleSummary

object SenderActiveScheduleConst {
    const val MODE_BLACKLIST = "blacklist"
    const val MODE_WHITELIST = "whitelist"
    val ALL_WEEKDAYS: List<Int>
        get() = ContractSenderActiveScheduleConst.ALL_WEEKDAYS

    fun parseMinutes(time: String): Int? = ContractSenderActiveScheduleConst.parseMinutes(time)
}

object SenderActiveScheduleEvaluator {
    fun sanitize(schedule: SenderActiveSchedule?): SenderActiveSchedule {
        return ContractSenderActiveScheduleEvaluator.sanitize(schedule)
    }

    fun sanitizeRule(rule: SenderActiveScheduleRule?): SenderActiveScheduleRule {
        return ContractSenderActiveScheduleEvaluator.sanitizeRule(rule)
    }

    fun summarize(schedule: SenderActiveSchedule?): SenderActiveScheduleSummary {
        return ContractSenderActiveScheduleEvaluator.summarize(schedule)
    }

    fun isAllowed(
        schedule: SenderActiveSchedule?,
        messageType: MessageType,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        return ContractSenderActiveScheduleEvaluator.isAllowed(schedule, messageType, now)
    }

    fun isRuleAllowed(
        rule: SenderActiveScheduleRule,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        return ContractSenderActiveScheduleEvaluator.isRuleAllowed(rule, now)
    }
}
