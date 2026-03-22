package io.github.magisk317.relay.platform.ipc

import android.content.Intent
import io.github.magisk317.relay.data.db.entity.SmsMsg
import java.util.UUID
import kotlin.math.abs

object ForwardPayloadFactory {
    fun ensureSmsEventId(intent: Intent): String {
        val existing = intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID).orEmpty().trim()
        if (existing.isNotEmpty()) {
            return existing
        }
        val generated = ForwardBroadcastContract.buildEventId("sms", abs(intent.hashCode()).toString(36))
        intent.putExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, generated)
        return generated
    }

    fun smsPayload(
        smsMsg: SmsMsg,
        eventId: String? = null,
        sourceIntent: Intent? = null,
    ): ForwardBroadcastPayload {
        val resolvedEventId = eventId?.takeIf { it.isNotBlank() }
            ?: ForwardBroadcastContract.buildEventId(
                prefix = "sms",
                seed = (smsMsg.sender ?: "") + (smsMsg.body ?: ""),
            )
        return ForwardBroadcastPayload(
            sender = smsMsg.sender,
            body = smsMsg.body,
            date = smsMsg.date,
            company = smsMsg.company,
            smsCode = smsMsg.smsCode,
            packageName = smsMsg.packageName,
            notifyChannelId = smsMsg.notifyChannelId,
            msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
            forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
            eventId = resolvedEventId,
        ).withSimRoutingFrom(sourceIntent)
    }

    fun appNotificationPayload(
        packageName: String,
        title: String,
        body: String,
        timestamp: Long,
        appName: String,
        notifyChannelId: String,
    ): ForwardBroadcastPayload {
        return ForwardBroadcastPayload(
            sender = title,
            body = body,
            date = timestamp,
            company = appName,
            smsCode = null,
            packageName = packageName,
            notifyChannelId = notifyChannelId,
            msgType = ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY,
            forwardSource = ForwardBroadcastContract.SOURCE_NOTIFICATION_LISTENER,
            eventId = ForwardBroadcastContract.buildEventId("nls", packageName),
        )
    }

    fun callPayload(
        packageName: String,
        sender: String,
        body: String,
        company: String,
        timestamp: Long,
        callType: Int,
        callStage: String,
    ): ForwardBroadcastPayload {
        return ForwardBroadcastPayload(
            sender = sender,
            body = body,
            date = timestamp,
            company = company,
            smsCode = null,
            packageName = packageName,
            notifyChannelId = "",
            msgType = ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY,
            forwardSource = ForwardBroadcastContract.SOURCE_TELEPHONY_STATE,
            eventId = "tel_${UUID.randomUUID().toString().take(8)}",
            callType = callType,
            callStage = callStage,
        )
    }
}
