package io.github.magisk317.relay.android.data.mapper

import io.github.magisk317.forward.db.ForwardEventEntity
import io.github.magisk317.forward.engine.model.ReadRecordData
import io.github.magisk317.forward.engine.model.ReadRecordDataImpl
import io.github.magisk317.smscode.db.entity.SmsMsg

/** Maps the forward-domain event record onto the engine read model. */
fun ForwardEventEntity.toRecordData(): ReadRecordData = ReadRecordDataImpl(
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

fun ReadRecordData.toForwardEventEntity(): ForwardEventEntity = ForwardEventEntity(
    id = id,
    eventId = "",
    msgType = msgType,
    sender = sender,
    body = body,
    date = date,
    processedTime = processedTime,
    company = company,
    smsCode = smsCode,
    packageName = packageName,
    notifyChannelId = notifyChannelId,
    callType = callType,
    simSlot = simSlot,
    subId = subId,
    contactName = contactName,
    phoneArea = phoneArea,
    sessionKey = sessionKey,
    forwardStatus = forwardStatus,
    forwardTarget = forwardTarget,
    forwardMessage = forwardMessage,
    forwardTime = forwardTime,
)

/** Port of the legacy sms_msg merge: richer record wins, incoming forward state wins. */
fun mergeForwardEventForInsert(existing: ForwardEventEntity, incoming: ForwardEventEntity): ForwardEventEntity {
    val incomingHasForwardState = incoming.forwardStatus != ForwardEventEntity.FORWARD_STATUS_NONE ||
        !incoming.forwardTarget.isNullOrBlank() ||
        !incoming.forwardMessage.isNullOrBlank() ||
        incoming.forwardTime > 0L
    val preferred = preferRicherEvent(existing, incoming)
    val fallback = if (preferred === existing) incoming else existing
    return existing.copy(
        sender = preferred.sender ?: fallback.sender ?: existing.sender,
        body = preferred.body ?: fallback.body ?: existing.body,
        date = incoming.date.takeIf { it > 0L } ?: existing.date,
        processedTime = existing.processedTime.takeIf { it > 0L }
            ?: incoming.processedTime.takeIf { it > 0L }
            ?: 0L,
        company = preferred.company?.takeIf { it.isNotBlank() }
            ?: fallback.company?.takeIf { it.isNotBlank() }
            ?: existing.company,
        smsCode = preferred.smsCode?.takeIf { it.isNotBlank() }
            ?: fallback.smsCode?.takeIf { it.isNotBlank() }
            ?: existing.smsCode,
        packageName = preferred.packageName?.takeIf { it.isNotBlank() }
            ?: fallback.packageName?.takeIf { it.isNotBlank() }
            ?: existing.packageName,
        notifyChannelId = preferred.notifyChannelId.takeIf { it.isNotBlank() }
            ?: fallback.notifyChannelId.takeIf { it.isNotBlank() }
            ?: existing.notifyChannelId,
        simSlot = preferred.simSlot.takeIf { it >= 0 }
            ?: fallback.simSlot.takeIf { it >= 0 }
            ?: existing.simSlot,
        subId = preferred.subId.takeIf { it > 0 }
            ?: fallback.subId.takeIf { it > 0 }
            ?: existing.subId,
        contactName = preferred.contactName.takeIf { it.isNotBlank() }
            ?: fallback.contactName.takeIf { it.isNotBlank() }
            ?: existing.contactName,
        phoneArea = preferred.phoneArea.takeIf { it.isNotBlank() }
            ?: fallback.phoneArea.takeIf { it.isNotBlank() }
            ?: existing.phoneArea,
        msgType = incoming.msgType,
        callType = preferred.callType.takeIf { it != 0 }
            ?: fallback.callType.takeIf { it != 0 }
            ?: existing.callType,
        sessionKey = preferred.sessionKey.takeIf { it.isNotBlank() }
            ?: fallback.sessionKey.takeIf { it.isNotBlank() }
            ?: existing.sessionKey,
        forwardStatus = if (incomingHasForwardState) incoming.forwardStatus else existing.forwardStatus,
        forwardTarget = if (incomingHasForwardState) incoming.forwardTarget else existing.forwardTarget,
        forwardMessage = if (incomingHasForwardState) incoming.forwardMessage else existing.forwardMessage,
        forwardTime = if (incomingHasForwardState) incoming.forwardTime else existing.forwardTime,
    )
}

private fun preferRicherEvent(existing: ForwardEventEntity, incoming: ForwardEventEntity): ForwardEventEntity {
    val existingScore = scoreEvent(existing)
    val incomingScore = scoreEvent(incoming)
    return when {
        incomingScore > existingScore -> incoming
        incomingScore < existingScore -> existing
        incoming.date > existing.date -> incoming
        else -> existing
    }
}

private fun scoreEvent(record: ForwardEventEntity): Int {
    var score = 0
    val pkg = record.packageName.orEmpty()
    if (pkg.isNotBlank() && !isSystemSmsPackage(pkg)) score += 4
    if (record.company.orEmpty().isNotBlank()) score += 2
    if (record.simSlot >= 0) score += 2
    if (record.sender.orEmpty().isNotBlank()) score += 1
    if (record.contactName.isNotBlank()) score += 1
    return score
}

private fun isSystemSmsPackage(packageName: String): Boolean {
    return packageName == "com.android.mms" ||
        packageName == "com.android.messaging" ||
        packageName == "com.google.android.apps.messaging"
}

/** Same projection as the legacy importer, usable wherever a SmsMsg crosses the seam. */
fun SmsMsg.toForwardEventEntity(): ForwardEventEntity = ForwardEventEntity(
    id = id ?: 0,
    eventId = "",
    msgType = msgType,
    sender = sender,
    body = body,
    date = date,
    processedTime = processedTime,
    company = company,
    smsCode = smsCode,
    packageName = packageName,
    notifyChannelId = notifyChannelId,
    callType = callType,
    simSlot = simSlot,
    subId = subId,
    contactName = contactName,
    phoneArea = phoneArea,
    sessionKey = sessionKey,
    forwardStatus = forwardStatus,
    forwardTarget = forwardTarget,
    forwardMessage = forwardMessage,
    forwardTime = forwardTime,
)
