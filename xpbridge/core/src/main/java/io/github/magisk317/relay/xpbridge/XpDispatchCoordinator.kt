package io.github.magisk317.relay.xpbridge

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.contract.xpbridge.NoopXpSmsDispatchRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpPreparedSmsHookDispatch
import io.github.magisk317.relay.contract.xpbridge.XpSmsDispatchRuntimeBridge

object XpDispatchCoordinator {
    @Volatile
    private var runtimeBridge: XpSmsDispatchRuntimeBridge = NoopXpSmsDispatchRuntimeBridge

    fun installRuntimeBridge(bridge: XpSmsDispatchRuntimeBridge?) {
        runtimeBridge = bridge ?: NoopXpSmsDispatchRuntimeBridge
    }

    fun ensureIncomingEventId(intent: Intent): String = runtimeBridge.ensureIncomingEventId(intent)

    fun parseIncomingSms(intent: Intent): SmsMsg? {
        return runtimeBridge.parseIncomingSms(intent)?.let(SmsMsg::fromRecord)
    }

    fun prepareParsedSms(
        smsMsg: SmsMsg,
        sourceIntent: Intent? = null,
        eventId: String? = null,
    ): PreparedSmsHookDispatch {
        return runtimeBridge.prepareParsedSms(
            smsMsg = smsMsg.toRecord(),
            sourceIntent = sourceIntent,
            eventId = eventId,
        ).toXpPreparedDispatch()
    }

    suspend fun prepareIngressSms(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        sourceIntent: Intent? = null,
        eventId: String? = null,
    ): PreparedSmsHookDispatch? {
        return runtimeBridge.prepareIngressSms(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg.toRecord(),
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
        return runtimeBridge.enrichObservedSms(
            phoneContext = phoneContext,
            sender = sender,
            body = body,
            date = date,
            smsCode = smsCode,
        ).let(SmsMsg::fromRecord)
    }

    fun dispatchPreparedSms(
        context: Context,
        prepared: PreparedSmsHookDispatch,
        sentFromUid: Int?,
    ): XpSmsHookDispatchResult {
        return runtimeBridge.dispatchPreparedSms(
            context = context,
            prepared = prepared.prepared,
            sentFromUid = sentFromUid,
        )
    }

    private fun XpPreparedSmsHookDispatch.toXpPreparedDispatch(): PreparedSmsHookDispatch {
        return PreparedSmsHookDispatch(
            prepared = this,
        )
    }
}
