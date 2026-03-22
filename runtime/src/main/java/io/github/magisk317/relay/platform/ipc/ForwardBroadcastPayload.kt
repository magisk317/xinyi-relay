package io.github.magisk317.relay.platform.ipc

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.domain.event.RelayEvent

data class ForwardBroadcastPayload(
    val sender: String? = null,
    val body: String? = null,
    val date: Long = 0L,
    val company: String? = null,
    val smsCode: String? = null,
    val packageName: String? = null,
    val notifyChannelId: String = "",
    val msgType: String = ForwardBroadcastContract.MSG_TYPE_SMS,
    val forwardSource: String = "unknown",
    val eventId: String = "",
    val callType: Int = 0,
    val callStage: String = "",
    val simSlot: Int? = null,
    val subId: Int? = null,
) {
    fun toIntent(
        context: Context,
        token: String? = null,
    ): Intent = ForwardReceiverIntentFactory.newHostIntent(context).apply {
        ForwardBroadcastContract.populatePayload(
            intent = this,
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
            callType = callType.takeIf { it != 0 || msgType == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY },
            callStage = callStage.ifBlank { null },
            simSlot = simSlot,
            subId = subId,
        )
        ForwardBroadcastContract.putIpcToken(this, token)
    }

    fun withSimRoutingFrom(intent: Intent?): ForwardBroadcastPayload {
        if (intent == null) return this
        return copy(
            simSlot = ForwardBroadcastContract.readIntExtra(
                intent,
                "slot",
                "simId",
                "sim_id",
                "simSlot",
                ForwardBroadcastContract.EXTRA_SIM_SLOT,
                "android.telephony.extra.SLOT_INDEX",
            ),
            subId = ForwardBroadcastContract.readIntExtra(
                intent,
                "subscription",
                "subscription_id",
                ForwardBroadcastContract.EXTRA_SUB_ID,
                "android.telephony.extra.SUBSCRIPTION_INDEX",
                "android.telephony.extra.SUBSCRIPTION_ID",
            ),
        )
    }

    fun toRelayEvent(
        contactName: String = "",
        phoneArea: String = "",
    ): RelayEvent {
        return RelayEvent(
            messageType = resolveRelayMessageType(),
            sourceType = forwardSource,
            sender = sender.orEmpty(),
            body = body.orEmpty(),
            timestamp = date,
            packageName = packageName.orEmpty(),
            notifyChannelId = notifyChannelId,
            companyOrAppName = company.orEmpty(),
            smsCode = smsCode,
            callType = callType,
            callStage = callStage,
            simSlot = simSlot ?: -1,
            subId = subId ?: 0,
            contactName = contactName,
            phoneArea = phoneArea,
        )
    }

    fun resolveRelayMessageType(): MessageType = when {
        msgType == ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY -> MessageType.APP_NOTIFY
        msgType == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY -> MessageType.CALL_NOTIFY
        !smsCode.isNullOrBlank() -> MessageType.SMS_CODE
        else -> MessageType.SMS_PLAIN
    }

    companion object {
        fun fromIntent(intent: Intent): ForwardBroadcastPayload {
            return ForwardBroadcastPayload(
                sender = intent.getStringExtra(ForwardBroadcastContract.EXTRA_SENDER),
                body = intent.getStringExtra(ForwardBroadcastContract.EXTRA_BODY),
                date = intent.getLongExtra(ForwardBroadcastContract.EXTRA_DATE, 0L),
                company = intent.getStringExtra(ForwardBroadcastContract.EXTRA_COMPANY),
                smsCode = intent.getStringExtra(ForwardBroadcastContract.EXTRA_SMS_CODE),
                packageName = intent.getStringExtra(ForwardBroadcastContract.EXTRA_PACKAGE_NAME),
                notifyChannelId = intent.getStringExtra(ForwardBroadcastContract.EXTRA_NOTIFY_CHANNEL_ID).orEmpty(),
                msgType = intent.getStringExtra(ForwardBroadcastContract.EXTRA_MSG_TYPE)
                    ?: ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = intent.getStringExtra(ForwardBroadcastContract.EXTRA_FORWARD_SOURCE) ?: "unknown",
                eventId = intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID).orEmpty(),
                callType = ForwardBroadcastContract.readIntExtra(
                    intent,
                    ForwardBroadcastContract.EXTRA_CALL_TYPE,
                ) ?: 0,
                callStage = intent.getStringExtra(ForwardBroadcastContract.EXTRA_CALL_STAGE).orEmpty(),
                simSlot = ForwardBroadcastContract.readIntExtra(
                    intent,
                    ForwardBroadcastContract.EXTRA_SIM_SLOT,
                    "slot",
                    "simId",
                    "sim_id",
                    "simSlot",
                    "android.telephony.extra.SLOT_INDEX",
                ),
                subId = ForwardBroadcastContract.readIntExtra(
                    intent,
                    ForwardBroadcastContract.EXTRA_SUB_ID,
                    "subscription",
                    "subscription_id",
                    "android.telephony.extra.SUBSCRIPTION_INDEX",
                    "android.telephony.extra.SUBSCRIPTION_ID",
                ),
            )
        }
    }
}
