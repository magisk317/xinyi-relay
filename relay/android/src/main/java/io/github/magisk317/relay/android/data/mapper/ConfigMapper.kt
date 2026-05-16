package io.github.magisk317.relay.android.data.mapper

import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.db.entity.ForwardFilterRuleEntity
import io.github.magisk317.relay.android.data.db.entity.RuleEntity
import io.github.magisk317.relay.android.data.db.entity.SenderEntity
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.engine.model.AppInfoData
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.model.Rule
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.model.SmsCodeRuleData
import io.github.magisk317.relay.engine.sender.SenderActiveSchedule
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleEvaluator
import io.github.magisk317.relay.contract.json.RelayJson

object ConfigMapper {
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
        priority = priority,
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
        activeScheduleJson = RelayJson.encode(
            SenderActiveSchedule.serializer(),
            SenderActiveScheduleEvaluator.sanitize(activeSchedule),
        ),
        priority = priority,
    )

    fun AppInfoData.toEntity(): AppInfo = when (this) {
        is AppInfo -> this
        else -> AppInfo(
            packageName = packageName,
            label = label,
            blocked = blocked,
            forwarding = forwarding,
            forwardingConfigured = forwardingConfigured,
            notifyTemplate = notifyTemplate,
        )
    }

    fun SmsCodeRuleData.toEntity(): SmsCodeRule = when (this) {
        is SmsCodeRule -> this
        else -> SmsCodeRule(
            company = company,
            codeKeyword = codeKeyword,
            codeRegex = codeRegex,
            id = id,
        )
    }

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
        val parsed = runCatching { RelayJson.decode(SenderActiveSchedule.serializer(), json) }.getOrNull()
        return SenderActiveScheduleEvaluator.sanitize(parsed)
    }
}
