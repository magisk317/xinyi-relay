package io.github.magisk317.relay.data.remote

import io.github.magisk317.smscode.db.entity.AppInfo
import io.github.magisk317.smscode.db.entity.NotifyRouteRule
import io.github.magisk317.smscode.db.entity.SmsCodeRule
import io.github.magisk317.relay.contract.model.SnapshotAppInfo
import io.github.magisk317.relay.contract.model.SnapshotForwardFilterRule
import io.github.magisk317.relay.contract.model.SnapshotNotifyRouteRule
import io.github.magisk317.relay.contract.model.SnapshotRule
import io.github.magisk317.relay.contract.model.SnapshotSender
import io.github.magisk317.relay.contract.model.SnapshotSmsCodeRule
import io.github.magisk317.forward.engine.model.ForwardFilterRule
import io.github.magisk317.forward.engine.model.Rule
import io.github.magisk317.forward.engine.model.Sender
import io.github.magisk317.forward.engine.sender.SenderActiveSchedule as ForwardSenderActiveSchedule
import io.github.magisk317.forward.engine.sender.SenderActiveScheduleRange as ForwardSenderActiveScheduleRange
import io.github.magisk317.forward.engine.sender.SenderActiveScheduleRule as ForwardSenderActiveScheduleRule
import io.github.magisk317.forward.contract.model.SenderActiveSchedule as LegacySenderActiveSchedule
import io.github.magisk317.forward.contract.model.SenderActiveScheduleRange as LegacySenderActiveScheduleRange
import io.github.magisk317.forward.contract.model.SenderActiveScheduleRule as LegacySenderActiveScheduleRule
import java.util.Date

internal fun Sender.toSnapshot(): SnapshotSender = SnapshotSender(
    id = id,
    type = type,
    name = name,
    jsonSetting = jsonSetting,
    status = status,
    time = time.time,
    receiveCode = receiveCode,
    receiveNonCode = receiveNonCode,
    receiveAppNotify = receiveAppNotify,
    receiveCallNotify = receiveCallNotify,
    activeSchedule = activeSchedule.toLegacySchedule(),
    priority = priority,
    customTemplate = customTemplate,
)

internal fun SnapshotSender.toSnapshotDomain(): Sender = Sender(
    id = id,
    type = type,
    name = name,
    jsonSetting = jsonSetting,
    status = status,
    time = Date(time),
    receiveCode = receiveCode,
    receiveNonCode = receiveNonCode,
    receiveAppNotify = receiveAppNotify,
    receiveCallNotify = receiveCallNotify,
    activeSchedule = activeSchedule.toForwardSchedule(),
    priority = priority,
    customTemplate = customTemplate,
)

internal fun Rule.toSnapshot(): SnapshotRule = SnapshotRule(
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
    time = time.time,
    senderList = senderList.map { it.toSnapshot() },
    senderLogic = senderLogic,
    silentPeriodStart = silentPeriodStart,
    silentPeriodEnd = silentPeriodEnd,
    silentDayOfWeek = silentDayOfWeek,
    title = title,
)

internal fun SnapshotRule.toSnapshotDomain(): Rule = Rule(
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
    time = Date(time),
    senderList = senderList.map { it.toSnapshotDomain() },
    senderLogic = senderLogic,
    silentPeriodStart = silentPeriodStart,
    silentPeriodEnd = silentPeriodEnd,
    silentDayOfWeek = silentDayOfWeek,
    title = title,
)

internal fun AppInfo.toSnapshot(): SnapshotAppInfo = SnapshotAppInfo(
    packageName = packageName,
    label = label,
    blocked = blocked,
    forwarding = forwarding,
    forwardingConfigured = forwardingConfigured,
    notifyTemplate = notifyTemplate,
)

internal fun SnapshotAppInfo.toSnapshotEntity(): AppInfo = AppInfo(
    packageName = packageName,
    label = label,
    blocked = blocked,
    forwarding = forwarding,
    forwardingConfigured = forwardingConfigured,
    notifyTemplate = notifyTemplate,
)

internal fun SmsCodeRule.toSnapshot(): SnapshotSmsCodeRule = SnapshotSmsCodeRule(
    company = company,
    codeKeyword = codeKeyword,
    codeRegex = codeRegex,
    id = id ?: 0L,
)

internal fun SnapshotSmsCodeRule.toSnapshotEntity(): SmsCodeRule = SmsCodeRule(
    company = company,
    codeKeyword = codeKeyword,
    codeRegex = codeRegex,
    id = id,
)

internal fun NotifyRouteRule.toSnapshot(): SnapshotNotifyRouteRule = SnapshotNotifyRouteRule(
    id = id,
    scope = scope,
    packageName = packageName,
    senderId = senderId,
    updateTime = updateTime,
)

internal fun SnapshotNotifyRouteRule.toSnapshotEntity(): NotifyRouteRule = NotifyRouteRule(
    id = id,
    scope = scope,
    packageName = packageName,
    senderId = senderId,
    updateTime = updateTime,
)

internal fun ForwardFilterRule.toSnapshot(): SnapshotForwardFilterRule = SnapshotForwardFilterRule(
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

internal fun SnapshotForwardFilterRule.toSnapshotDomain(): ForwardFilterRule = ForwardFilterRule(
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

private fun ForwardSenderActiveSchedule.toLegacySchedule(): LegacySenderActiveSchedule =
    LegacySenderActiveSchedule(
        sms = sms.toLegacyRule(),
        appNotify = appNotify.toLegacyRule(),
        callNotify = callNotify.toLegacyRule(),
    )

private fun ForwardSenderActiveScheduleRule.toLegacyRule(): LegacySenderActiveScheduleRule =
    LegacySenderActiveScheduleRule(
        enabled = enabled,
        mode = mode,
        weekdays = weekdays,
        ranges = ranges.map { LegacySenderActiveScheduleRange(it.start, it.end) },
    )

private fun LegacySenderActiveSchedule.toForwardSchedule(): ForwardSenderActiveSchedule =
    ForwardSenderActiveSchedule(
        sms = sms.toForwardRule(),
        appNotify = appNotify.toForwardRule(),
        callNotify = callNotify.toForwardRule(),
    )

private fun LegacySenderActiveScheduleRule.toForwardRule(): ForwardSenderActiveScheduleRule =
    ForwardSenderActiveScheduleRule(
        enabled = enabled,
        mode = mode,
        weekdays = weekdays,
        ranges = ranges.map { ForwardSenderActiveScheduleRange(it.start, it.end) },
    )
