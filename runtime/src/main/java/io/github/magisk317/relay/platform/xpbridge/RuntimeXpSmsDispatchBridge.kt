package io.github.magisk317.relay.platform.xpbridge

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.contract.xpbridge.XpForwardPayload
import io.github.magisk317.relay.contract.xpbridge.XpMessageType
import io.github.magisk317.relay.contract.xpbridge.XpPreparedSmsHookDispatch
import io.github.magisk317.relay.contract.xpbridge.XpSmsDispatchRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpSmsHookDispatchResult
import io.github.magisk317.relay.contract.xpbridge.XpSmsRecord
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastContract
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastPayload
import io.github.magisk317.relay.platform.ipc.PreparedSmsHookDispatch
import io.github.magisk317.relay.platform.ipc.SmsHookDispatchCoordinator

object RuntimeXpSmsDispatchBridge : XpSmsDispatchRuntimeBridge {
    override fun ensureIncomingEventId(intent: Intent): String {
        return SmsHookDispatchCoordinator.ensureIncomingEventId(intent)
    }

    override fun parseIncomingSms(intent: Intent): XpSmsRecord? {
        return SmsHookDispatchCoordinator.parseIncomingSms(intent)?.toXpSmsRecord()
    }

    override fun prepareParsedSms(
        smsMsg: XpSmsRecord,
        sourceIntent: Intent?,
        eventId: String?,
    ): XpPreparedSmsHookDispatch {
        return SmsHookDispatchCoordinator.prepareParsedSms(
            smsMsg = smsMsg.toRuntimeSmsMsg(),
            sourceIntent = sourceIntent,
            eventId = eventId,
        ).toXpPreparedDispatch()
    }

    override suspend fun prepareIngressSms(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: XpSmsRecord,
        sourceIntent: Intent?,
        eventId: String?,
    ): XpPreparedSmsHookDispatch? {
        return SmsHookDispatchCoordinator.prepareIngressSms(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg.toRuntimeSmsMsg(),
            sourceIntent = sourceIntent,
            eventId = eventId,
        )?.toXpPreparedDispatch()
    }

    override fun enrichObservedSms(
        phoneContext: Context,
        sender: String,
        body: String,
        date: Long,
        smsCode: String,
    ): XpSmsRecord {
        return SmsHookDispatchCoordinator.enrichObservedSms(
            phoneContext = phoneContext,
            sender = sender,
            body = body,
            date = date,
            smsCode = smsCode,
        ).toXpSmsRecord()
    }

    override fun dispatchPreparedSms(
        context: Context,
        prepared: XpPreparedSmsHookDispatch,
        sentFromUid: Int?,
    ): XpSmsHookDispatchResult {
        val result = SmsHookDispatchCoordinator.dispatchPreparedSms(
            context = context,
            prepared = prepared.toRuntimePreparedDispatch(),
            sentFromUid = sentFromUid,
        )
        return XpSmsHookDispatchResult(
            dispatched = result.dispatched,
            bypassUsed = result.bypassUsed,
            tokenPresent = result.tokenPresent,
        )
    }

    private fun PreparedSmsHookDispatch.toXpPreparedDispatch(): XpPreparedSmsHookDispatch {
        return XpPreparedSmsHookDispatch(
            smsMsg = smsMsg.toXpSmsRecord(),
            payload = payload.toXpForwardPayload(),
            messageType = messageType?.toXpMessageType(),
        )
    }

    private fun XpPreparedSmsHookDispatch.toRuntimePreparedDispatch(): PreparedSmsHookDispatch {
        return PreparedSmsHookDispatch(
            smsMsg = smsMsg.toRuntimeSmsMsg(),
            payload = payload.toRuntimePayload(),
            messageType = messageType?.toRuntimeMessageType(),
        )
    }

    private fun SmsMsg.toXpSmsRecord(): XpSmsRecord {
        return XpSmsRecord(
            id = id,
            sender = sender,
            body = body,
            date = date,
            processedTime = processedTime,
            company = company,
            smsCode = smsCode,
            packageName = packageName,
            notifyChannelId = notifyChannelId,
            forwardStatus = forwardStatus,
            forwardTarget = forwardTarget,
            forwardMessage = forwardMessage,
            forwardTime = forwardTime,
            msgType = msgType,
            callType = callType,
        )
    }

    private fun XpSmsRecord.toRuntimeSmsMsg(): SmsMsg {
        return SmsMsg(
            id = id,
            sender = sender,
            body = body,
            date = date,
            processedTime = processedTime,
            company = company,
            smsCode = smsCode,
            packageName = packageName,
            notifyChannelId = notifyChannelId,
            forwardStatus = forwardStatus,
            forwardTarget = forwardTarget,
            forwardMessage = forwardMessage,
            forwardTime = forwardTime,
            msgType = msgType,
            callType = callType,
        )
    }

    private fun ForwardBroadcastPayload.toXpForwardPayload(): XpForwardPayload {
        return XpForwardPayload(
            sender = sender,
            body = body,
            date = date,
            company = company,
            smsCode = smsCode,
            packageName = packageName,
            notifyChannelId = notifyChannelId,
            msgType = msgType,
            forwardSource = forwardSource,
            eventId = eventId,
            callType = callType,
            callStage = callStage,
            simSlot = simSlot,
            subId = subId,
        )
    }

    private fun XpForwardPayload.toRuntimePayload(): ForwardBroadcastPayload {
        return ForwardBroadcastPayload(
            sender = sender,
            body = body,
            date = date,
            company = company,
            smsCode = smsCode,
            packageName = packageName,
            notifyChannelId = notifyChannelId,
            msgType = msgType,
            forwardSource = forwardSource,
            eventId = eventId,
            callType = callType,
            callStage = callStage,
            simSlot = simSlot,
            subId = subId,
        )
    }

    private fun MessageType.toXpMessageType(): XpMessageType {
        return when (this) {
            MessageType.SMS_CODE -> XpMessageType.SMS_CODE
            MessageType.SMS_PLAIN -> XpMessageType.SMS_PLAIN
            MessageType.APP_NOTIFY -> XpMessageType.APP_NOTIFY
            MessageType.CALL_NOTIFY -> XpMessageType.CALL_NOTIFY
        }
    }

    private fun XpMessageType.toRuntimeMessageType(): MessageType {
        return when (this) {
            XpMessageType.SMS_CODE -> MessageType.SMS_CODE
            XpMessageType.SMS_PLAIN -> MessageType.SMS_PLAIN
            XpMessageType.APP_NOTIFY -> MessageType.APP_NOTIFY
            XpMessageType.CALL_NOTIFY -> MessageType.CALL_NOTIFY
        }
    }
}
