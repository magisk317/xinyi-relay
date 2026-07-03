package io.github.magisk317.relay.receiver

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.platform.ipc.EventDeduplicator
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastPayload
import io.github.magisk317.relay.platform.ipc.ForwardPayloadFactory
import io.github.magisk317.relay.platform.ipc.SmsIngressAdapter

object StandardMessageIngressHandler {
    fun isSmsReceived(intent: Intent): Boolean {
        return intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
    }

    fun isMmsWapPush(intent: Intent): Boolean {
        val isWapPush = intent.action == Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION ||
            intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION
        if (!isWapPush) return false
        return intent.type.isNullOrBlank() || intent.type == MMS_MIME_TYPE
    }

    fun shouldHandleStandardMode(context: Context, source: String): Boolean {
        val appContext = context.applicationContext
        WorkModeResolver.resolve(appContext)
        val mode = WorkModeResolver.mode.value
        if (mode != WorkMode.Standard) {
            XLog.i("%s: mode=%s, skipping standard ingress", source, mode)
            return false
        }
        return true
    }

    suspend fun dispatchSms(context: Context, intent: Intent) {
        val smsMsg = SmsMsg.fromIntent(intent)
        val payload = buildSmsPayload(
            context = context,
            smsMsg = smsMsg,
            intent = intent,
        ) ?: return
        if (EventDeduplicator.isDuplicate(payload.eventId)) {
            XLog.i("StandardSmsReceiver: Duplicate event skipped (eventId=%s)", payload.eventId)
            return
        }
        ForwardBroadcastDispatcher.dispatchFromHost(
            context = context,
            payload = payload,
        )
    }

    internal suspend fun buildSmsPayload(
        context: Context,
        smsMsg: SmsMsg,
        intent: Intent,
        smsCodeParser: (suspend (Context, String) -> String)? = null,
    ): ForwardBroadcastPayload? {
        if (smsMsg.sender.isNullOrBlank() || smsMsg.body.isNullOrBlank()) {
            XLog.e("StandardSmsReceiver: Failed to parse SMS")
            return null
        }

        val eventId = ForwardPayloadFactory.ensureSmsEventId(intent, smsMsg)
        XLog.i("StandardSmsReceiver: Intercepted SMS from %s (eventId=%s)", smsMsg.sender ?: "<null>", eventId)
        val result = SmsIngressAdapter.toPayload(
            pluginContext = context.applicationContext,
            phoneContext = context,
            smsMsg = smsMsg,
            sourceIntent = intent,
            eventId = eventId,
            smsCodeParser = smsCodeParser,
        ) ?: return null
        return result.payload
    }

    suspend fun dispatchMms(context: Context, intent: Intent) {
        val payload = ForwardPayloadFactory.mmsPayload(intent)
        if (EventDeduplicator.isDuplicate(payload.eventId)) {
            XLog.i("StandardMmsReceiver: Duplicate event skipped (eventId=%s)", payload.eventId)
            return
        }
        XLog.i("StandardMmsReceiver: Intercepted MMS eventId=%s", payload.eventId)
        ForwardBroadcastDispatcher.dispatchFromHost(
            context = context,
            payload = payload,
        )
    }

    private const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
}
