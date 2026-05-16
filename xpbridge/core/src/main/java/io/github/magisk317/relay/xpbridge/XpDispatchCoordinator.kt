package io.github.magisk317.relay.xpbridge

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.platform.ipc.SmsHookDispatchCoordinator
import io.github.magisk317.relay.platform.ipc.PreparedSmsHookDispatch as RuntimePreparedSmsHookDispatch

object XpDispatchCoordinator {
    fun ensureIncomingEventId(intent: Intent): String = SmsHookDispatchCoordinator.ensureIncomingEventId(intent)

    fun parseIncomingSms(intent: Intent): SmsMsg? {
        return SmsHookDispatchCoordinator.parseIncomingSms(intent)?.let(SmsMsg::fromRuntime)
    }

    fun prepareParsedSms(
        smsMsg: SmsMsg,
        sourceIntent: Intent? = null,
        eventId: String? = null,
    ): PreparedSmsHookDispatch {
        val prepared = SmsHookDispatchCoordinator.prepareParsedSms(
            smsMsg = smsMsg.toRuntime(),
            sourceIntent = sourceIntent,
            eventId = eventId,
        )
        return prepared.toXpPreparedDispatch()
    }

    suspend fun prepareIngressSms(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        sourceIntent: Intent? = null,
        eventId: String? = null,
    ): PreparedSmsHookDispatch? {
        return SmsHookDispatchCoordinator.prepareIngressSms(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg.toRuntime(),
            sourceIntent = sourceIntent,
            eventId = eventId,
        )?.toXpPreparedDispatch()
    }

    fun enrichObservedSms(
        phoneContext: Context,
        sender: String,
        body: String,
        date: Long,
        smsCode: String,
    ): SmsMsg {
        return SmsHookDispatchCoordinator.enrichObservedSms(
            phoneContext = phoneContext,
            sender = sender,
            body = body,
            date = date,
            smsCode = smsCode,
        ).let(SmsMsg::fromRuntime)
    }

    fun dispatchPreparedSms(
        context: Context,
        prepared: PreparedSmsHookDispatch,
        sentFromUid: Int?,
    ): XpSmsHookDispatchResult {
        val result = SmsHookDispatchCoordinator.dispatchPreparedSms(
            context = context,
            prepared = prepared.runtimePrepared,
            sentFromUid = sentFromUid,
        )
        return XpSmsHookDispatchResult(
            dispatched = result.dispatched,
            bypassUsed = result.bypassUsed,
            tokenPresent = result.tokenPresent,
        )
    }

    private fun RuntimePreparedSmsHookDispatch.toXpPreparedDispatch(): PreparedSmsHookDispatch {
        return PreparedSmsHookDispatch(
            runtimePrepared = this,
            smsMsg = SmsMsg.fromRuntime(smsMsg),
            messageType = messageType?.toXpMessageType(),
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
}
