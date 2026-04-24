package io.github.magisk317.relay.data.mapper

import io.github.magisk317.relay.data.db.entity.ForwardFilterRuleEntity
import io.github.magisk317.relay.data.db.entity.RuleEntity
import io.github.magisk317.relay.data.db.entity.SenderEntity
import io.github.magisk317.relay.domain.model.ForwardFilterRule
import io.github.magisk317.relay.domain.model.Rule
import io.github.magisk317.relay.domain.model.Sender
import io.github.magisk317.relay.domain.sender.SenderActiveSchedule
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleEvaluator
import com.google.gson.Gson

object ConfigMapper {
    private val gson = Gson()

    fun RuleEntity.toDomain(): Rule = Rule(
        id = id,
        type = type,
        filed = filed,
        check = check,
        value = value,
        senderId = senderId,
        smsTemplate = smsTemplate,
        regexReplace = regexReplace,
        simSlot = simSlot,
        status = status,
        time = time,
        senderList = senderList.map { it.toDomain() },
        senderLogic = senderLogic,
        silentPeriodStart = silentPeriodStart,
        silentPeriodEnd = silentPeriodEnd,
        silentDayOfWeek = silentDayOfWeek,
        title = title
    )

    fun Rule.toEntity(): RuleEntity = RuleEntity(
        id = id,
        type = type,
        filed = filed,
        check = check,
        value = value,
        senderId = senderId,
        smsTemplate = smsTemplate,
        regexReplace = regexReplace,
        simSlot = simSlot,
        status = status,
        time = time,
        senderList = senderList.map { it.toEntity() },
        senderLogic = senderLogic,
        silentPeriodStart = silentPeriodStart,
        silentPeriodEnd = silentPeriodEnd,
        silentDayOfWeek = silentDayOfWeek,
        title = title
    )

    fun SenderEntity.toDomain(): Sender = Sender(
        id = id,
        type = type,
        name = name,
        jsonSetting = jsonSetting,
        status = status,
        time = time,
        receiveCode = receiveCode,
        receiveNonCode = receiveNonCode,
        receiveAppNotify = receiveAppNotify,
        receiveCallNotify = receiveCallNotify,
        activeSchedule = parseActiveSchedule(activeScheduleJson),
    )

    fun Sender.toEntity(): SenderEntity = SenderEntity(
        id = id,
        type = type,
        name = name,
        jsonSetting = jsonSetting,
        status = status,
        time = time,
        receiveCode = receiveCode,
        receiveNonCode = receiveNonCode,
        receiveAppNotify = receiveAppNotify,
        receiveCallNotify = receiveCallNotify,
        activeScheduleJson = gson.toJson(SenderActiveScheduleEvaluator.sanitize(activeSchedule))
    )

    fun ForwardFilterRuleEntity.toDomain(): ForwardFilterRule = ForwardFilterRule(
        id = id,
        msgType = msgType,
        scopeType = scopeType,
        scopeKey = scopeKey,
        senderId = senderId,
        policy = policy,
        matchMode = matchMode,
        pattern = pattern,
        enabled = enabled,
        updateTime = updateTime
    )

    fun ForwardFilterRule.toEntity(): ForwardFilterRuleEntity = ForwardFilterRuleEntity(
        id = id,
        msgType = msgType,
        scopeType = scopeType,
        scopeKey = scopeKey,
        senderId = senderId,
        policy = policy,
        matchMode = matchMode,
        pattern = pattern,
        enabled = enabled,
        updateTime = updateTime
    )

    private fun parseActiveSchedule(json: String): SenderActiveSchedule {
        if (json.isBlank()) return SenderActiveSchedule()
        val parsed = runCatching { gson.fromJson(json, SenderActiveSchedule::class.java) }.getOrNull()
        return SenderActiveScheduleEvaluator.sanitize(parsed)
    }
}
