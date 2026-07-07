package io.github.magisk317.relay.data.remote

import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.contract.model.SnapshotAppInfo
import io.github.magisk317.relay.contract.model.SnapshotForwardFilterRule
import io.github.magisk317.relay.contract.model.SnapshotNotifyRouteRule
import io.github.magisk317.relay.contract.model.SnapshotRule
import io.github.magisk317.relay.contract.model.SnapshotSender
import io.github.magisk317.relay.contract.model.SnapshotSmsCodeRule
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.model.Rule
import io.github.magisk317.relay.engine.model.Sender
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
    activeSchedule = activeSchedule,
    priority = priority,
    customTemplate = customTemplate,
)

internal fun SnapshotSender.toDomain(): Sender = Sender(
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
    activeSchedule = activeSchedule,
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

internal fun SnapshotRule.toDomain(): Rule = Rule(
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
    senderList = senderList.map { it.toDomain() },
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

internal fun SnapshotAppInfo.toEntity(): AppInfo = AppInfo(
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
    id = id,
)

internal fun SnapshotSmsCodeRule.toEntity(): SmsCodeRule = SmsCodeRule(
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

internal fun SnapshotNotifyRouteRule.toEntity(): NotifyRouteRule = NotifyRouteRule(
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

internal fun SnapshotForwardFilterRule.toDomain(): ForwardFilterRule = ForwardFilterRule(
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
