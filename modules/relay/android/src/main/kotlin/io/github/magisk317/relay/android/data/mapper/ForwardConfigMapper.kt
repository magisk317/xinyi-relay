package io.github.magisk317.relay.android.data.mapper

import io.github.magisk317.forward.contract.json.RelayJson
import io.github.magisk317.forward.db.ForwardAppInfoEntity
import io.github.magisk317.forward.db.ForwardFilterRuleEntity
import io.github.magisk317.forward.db.ForwardRouteRuleEntity
import io.github.magisk317.forward.db.ForwardRuleEntity
import io.github.magisk317.forward.db.ForwardSenderEntity
import io.github.magisk317.forward.engine.model.AppInfoData
import io.github.magisk317.forward.engine.model.AppInfoImpl
import io.github.magisk317.forward.engine.model.ForwardFilterRule
import io.github.magisk317.forward.engine.model.NotifyRouteRuleData
import io.github.magisk317.forward.engine.model.NotifyRouteRuleImpl
import io.github.magisk317.forward.engine.model.Rule
import io.github.magisk317.forward.engine.model.Sender
import io.github.magisk317.forward.engine.sender.SenderActiveSchedule
import io.github.magisk317.forward.engine.sender.SenderActiveScheduleEvaluator
import kotlinx.serialization.builtins.ListSerializer
import java.util.Date

object ForwardConfigMapper {
    fun ForwardAppInfoEntity.toDomain(): AppInfoData = AppInfoImpl(
        packageName = packageName,
        label = label,
        blocked = blocked,
        forwarding = forwarding,
        forwardingConfigured = forwardingConfigured,
        notifyTemplate = notifyTemplate,
    )

    fun AppInfoData.toForwardEntity(): ForwardAppInfoEntity = ForwardAppInfoEntity(
        packageName = packageName,
        label = label,
        blocked = blocked,
        forwarding = forwarding,
        forwardingConfigured = forwardingConfigured,
        notifyTemplate = notifyTemplate,
    )

    fun ForwardSenderEntity.toDomain(): Sender = Sender(
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
        activeSchedule = runCatching {
            RelayJson.decode(SenderActiveSchedule.serializer(), activeScheduleJson)
        }.getOrDefault(SenderActiveSchedule()),
        priority = priority,
        customTemplate = customTemplate,
    )

    fun Sender.toForwardEntity(): ForwardSenderEntity = ForwardSenderEntity(
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
        customTemplate = customTemplate,
    )

    fun ForwardRouteRuleEntity.toDomain(): NotifyRouteRuleData = NotifyRouteRuleImpl(
        id = id,
        scope = scope,
        packageName = packageName,
        senderId = senderId,
        updateTime = updateTime,
    )

    fun NotifyRouteRuleData.toForwardEntity(): ForwardRouteRuleEntity = ForwardRouteRuleEntity(
        id = id,
        scope = scope,
        packageName = packageName,
        senderId = senderId,
        updateTime = updateTime,
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
        updateTime = updateTime,
    )

    fun ForwardFilterRule.toForwardEntity(): ForwardFilterRuleEntity = ForwardFilterRuleEntity(
        id = id,
        msgType = msgType,
        scopeType = scopeType,
        scopeKey = scopeKey,
        senderId = senderId,
        policy = policy,
        matchMode = matchMode,
        pattern = pattern,
        enabled = enabled,
        updateTime = updateTime,
    )

    fun ForwardRuleEntity.toDomain(): Rule = Rule(
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
        senderList = runCatching {
            RelayJson.decode(ListSerializer(Sender.serializer()), senderListJson)
        }.getOrDefault(emptyList()),
        senderLogic = senderLogic,
        silentPeriodStart = silentPeriodStart,
        silentPeriodEnd = silentPeriodEnd,
        silentDayOfWeek = silentDayOfWeek,
        title = title,
    )

    fun Rule.toForwardEntity(): ForwardRuleEntity = ForwardRuleEntity(
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
        senderListJson = RelayJson.encode(ListSerializer(Sender.serializer()), senderList),
        senderLogic = senderLogic,
        silentPeriodStart = silentPeriodStart,
        silentPeriodEnd = silentPeriodEnd,
        silentDayOfWeek = silentDayOfWeek,
        title = title,
    )
}
