package io.github.magisk317.relay.platform.ipc

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.data.db.entity.SmsMsg

data class PreparedSmsHookDispatch(
    val smsMsg: SmsMsg,
    val payload: ForwardBroadcastPayload,
    val messageType: MessageType? = null,
)

object SmsHookDispatchCoordinator {
    fun ensureIncomingEventId(
        intent: Intent,
        eventIdResolver: (Intent) -> String = ForwardPayloadFactory::ensureSmsEventId,
    ): String {
        return eventIdResolver(intent)
    }

    fun parseIncomingSms(
        intent: Intent,
        smsParser: (Intent) -> SmsMsg = SmsMsg::fromIntent,
    ): SmsMsg? {
        return runCatching { smsParser(intent) }.getOrNull()
    }

    fun prepareParsedSms(
        smsMsg: SmsMsg,
        sourceIntent: Intent? = null,
        eventId: String? = null,
        payloadFactory: (SmsMsg, String?, Intent?) -> ForwardBroadcastPayload = ForwardPayloadFactory::smsPayload,
    ): PreparedSmsHookDispatch {
        return PreparedSmsHookDispatch(
            smsMsg = smsMsg,
            payload = payloadFactory(smsMsg, eventId, sourceIntent),
        )
    }

    suspend fun prepareIngressSms(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        sourceIntent: Intent? = null,
        eventId: String? = null,
        ingressAdapter: suspend (Context, Context, SmsMsg, Intent?, String?) -> SmsIngressAdapter.Result? = SmsIngressAdapter::toPayload,
    ): PreparedSmsHookDispatch? {
        val result = ingressAdapter(pluginContext, phoneContext, smsMsg, sourceIntent, eventId) ?: return null
        return PreparedSmsHookDispatch(
            smsMsg = result.smsMsg,
            payload = result.payload,
            messageType = result.messageType,
        )
    }

    fun enrichObservedSms(
        phoneContext: Context,
        sender: String,
        body: String,
        date: Long,
        smsCode: String,
        enricher: (Context, SmsMsg, String?) -> SmsMsg = SmsIngressAdapter::enrichSmsMsg,
    ): SmsMsg {
        return enricher(
            phoneContext,
            SmsMsg(
                sender = sender,
                body = body,
                date = date,
                msgType = SmsMsg.MSG_TYPE_SMS,
            ),
            smsCode,
        )
    }

    fun dispatchPreparedSms(
        context: Context,
        prepared: PreparedSmsHookDispatch,
        sentFromUid: Int?,
        sdkInt: Int = android.os.Build.VERSION.SDK_INT,
        tokenResolver: (Context) -> String = io.github.magisk317.relay.prefs.PrefsReader::getIpcToken,
        dispatchBlock: (String?) -> Unit = { resolvedToken ->
            ForwardBroadcastDispatcher.dispatch(
                context = context,
                payload = prepared.payload,
                token = resolvedToken,
            )
        },
    ): SmsHookDispatchResult {
        return ForwardBroadcastDispatcher.dispatchFromSmsHook(
            context = context,
            payload = prepared.payload,
            sentFromUid = sentFromUid,
            sdkInt = sdkInt,
            tokenResolver = tokenResolver,
            dispatchBlock = dispatchBlock,
        )
    }
}
