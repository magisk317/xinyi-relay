package io.github.magisk317.relay.android.data.mapper

import io.github.magisk317.smscode.db.entity.AppInfo
import io.github.magisk317.smscode.db.entity.ForwardFilterRule
import io.github.magisk317.smscode.db.entity.Rule
import io.github.magisk317.smscode.db.entity.Sender
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.db.entity.SmsCodeRule
import io.github.magisk317.smscode.db.entity.NotifyRouteRule
import io.github.magisk317.relay.engine.model.AppInfoData
import io.github.magisk317.relay.engine.model.AppInfoImpl
import io.github.magisk317.relay.engine.model.SmsCodeRuleData
import io.github.magisk317.relay.engine.model.SmsCodeRuleImpl
import io.github.magisk317.relay.engine.model.NotifyRouteRuleData
import io.github.magisk317.relay.engine.model.ReadRecordData
import io.github.magisk317.relay.engine.model.ReadRecordDataImpl
import io.github.magisk317.relay.engine.model.NotifyRouteRuleImpl
import io.github.magisk317.relay.engine.sender.SenderActiveSchedule
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleEvaluator
import io.github.magisk317.relay.contract.json.RelayJson

object ConfigMapper {
    fun Rule.toDomain(): io.github.magisk317.relay.engine.model.Rule = io.github.magisk317.relay.engine.model.Rule(
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

    fun io.github.magisk317.relay.engine.model.Rule.toEntity(): Rule = Rule(
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

    fun Sender.toDomain(): io.github.magisk317.relay.engine.model.Sender = io.github.magisk317.relay.engine.model.Sender(
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
        customTemplate = customTemplate,
    )

    fun io.github.magisk317.relay.engine.model.Sender.toEntity(): Sender = Sender(
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

    fun AppInfoData.toEntity(): AppInfo = AppInfo(
        packageName = packageName,
        label = label,
        blocked = blocked,
        forwarding = forwarding,
        forwardingConfigured = forwardingConfigured,
        notifyTemplate = notifyTemplate,
    )

    fun AppInfo.toDomain(): AppInfoData = AppInfoImpl(
        packageName = packageName,
        label = label,
        blocked = blocked,
        forwarding = forwarding,
        forwardingConfigured = forwardingConfigured,
        notifyTemplate = notifyTemplate,
    )

    fun SmsCodeRuleData.toEntity(): SmsCodeRule = SmsCodeRule(
        company = company,
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
        id = id,
    )

    fun SmsCodeRule.toDomain(): SmsCodeRuleData = SmsCodeRuleImpl(
        id = id ?: 0L,
        company = company,
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
    )

    fun NotifyRouteRuleData.toEntity(): NotifyRouteRule = NotifyRouteRule(
        id = id,
        scope = scope,
        packageName = packageName,
        senderId = senderId,
        updateTime = updateTime,
    )

    fun NotifyRouteRule.toDomain(): NotifyRouteRuleData = NotifyRouteRuleImpl(
        id = id,
        scope = scope,
        packageName = packageName,
        senderId = senderId,
        updateTime = updateTime,
    )

    fun ReadRecordData.toSmsMsgEntity(): SmsMsg = SmsMsg(
        id = id,
        sender = sender,
        body = body,
        date = date,
        processedTime = processedTime,
        company = company,
        smsCode = smsCode,
        packageName = packageName,
        notifyChannelId = notifyChannelId,
        simSlot = simSlot,
        subId = subId,
        contactName = contactName,
        phoneArea = phoneArea,
        forwardStatus = forwardStatus,
        forwardTarget = forwardTarget,
        forwardMessage = forwardMessage,
        forwardTime = forwardTime,
        msgType = msgType,
        callType = callType,
        sessionKey = sessionKey,
    )

    fun SmsMsg.toRecordData(): ReadRecordData = ReadRecordDataImpl(
        id = id ?: 0L,
        sender = sender,
        body = body,
        date = date,
        processedTime = processedTime,
        company = company,
        smsCode = smsCode,
        packageName = packageName,
        notifyChannelId = notifyChannelId,
        simSlot = simSlot,
        subId = subId,
        contactName = contactName,
        phoneArea = phoneArea,
        forwardStatus = forwardStatus,
        forwardTarget = forwardTarget,
        forwardMessage = forwardMessage,
        forwardTime = forwardTime,
        msgType = msgType,
        callType = callType,
        sessionKey = sessionKey,
    )

    fun ForwardFilterRule.toDomain(): io.github.magisk317.relay.engine.model.ForwardFilterRule = io.github.magisk317.relay.engine.model.ForwardFilterRule(
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

    fun io.github.magisk317.relay.engine.model.ForwardFilterRule.toEntity(): ForwardFilterRule = ForwardFilterRule(
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
