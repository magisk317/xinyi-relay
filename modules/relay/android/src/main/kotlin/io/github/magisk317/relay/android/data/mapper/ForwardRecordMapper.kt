package io.github.magisk317.relay.android.data.mapper

import io.github.magisk317.forward.db.ForwardEventEntity
import io.github.magisk317.forward.engine.model.ReadRecordData
import io.github.magisk317.forward.engine.model.ReadRecordDataImpl

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
