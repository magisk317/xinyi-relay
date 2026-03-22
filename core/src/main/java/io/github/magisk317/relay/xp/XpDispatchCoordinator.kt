package io.github.magisk317.relay.xp

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.platform.ipc.PreparedSmsHookDispatch
import io.github.magisk317.relay.platform.ipc.SmsHookDispatchCoordinator
import io.github.magisk317.relay.platform.ipc.SmsHookDispatchResult

object XpDispatchCoordinator {
    fun ensureIncomingEventId(intent: Intent): String {
        return SmsHookDispatchCoordinator.ensureIncomingEventId(intent)
    }

    fun parseIncomingSms(intent: Intent): SmsMsg? {
        return SmsHookDispatchCoordinator.parseIncomingSms(intent)
    }

    fun prepareParsedSms(
        smsMsg: SmsMsg,
        sourceIntent: Intent? = null,
        eventId: String? = null,
    ): PreparedSmsHookDispatch {
        return SmsHookDispatchCoordinator.prepareParsedSms(
            smsMsg = smsMsg,
            sourceIntent = sourceIntent,
            eventId = eventId,
        )
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
            smsMsg = smsMsg,
            sourceIntent = sourceIntent,
            eventId = eventId,
        )
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
        )
    }

    fun dispatchPreparedSms(
        context: Context,
        prepared: PreparedSmsHookDispatch,
        sentFromUid: Int?,
    ): SmsHookDispatchResult {
        return SmsHookDispatchCoordinator.dispatchPreparedSms(
            context = context,
            prepared = prepared,
            sentFromUid = sentFromUid,
        )
    }
}
