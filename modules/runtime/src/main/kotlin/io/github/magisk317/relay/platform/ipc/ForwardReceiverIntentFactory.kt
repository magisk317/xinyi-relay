package io.github.magisk317.relay.platform.ipc

import android.content.Context
import android.content.Intent
import io.github.magisk317.forward.contract.ForwardMessageType
import io.github.magisk317.forward.runtime.ingress.ForwardBroadcastContract
import io.github.magisk317.forward.runtime.ingress.ForwardBroadcastPayload
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst

object ForwardReceiverIntentFactory {
    fun newHostIntent(context: Context): Intent = Intent(PrefConst.ACTION_FORWARD_SMS).apply {
        setClassName(context.packageName, ForwardReceiver::class.java.name)
        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
}

/** Composes the wire intent for a forward payload; the base intent stays product-owned. */
fun ForwardBroadcastPayload.toHostIntent(context: Context, token: String? = null): Intent =
    ForwardReceiverIntentFactory.newHostIntent(context).apply {
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
            callType = callType.takeIf { it != 0 || msgType == ForwardMessageType.CALL_NOTIFICATION },
            callStage = callStage.ifBlank { null },
            simSlot = simSlot,
            subId = subId,
            appIcon = appIcon,
        )
        ForwardBroadcastContract.putIpcToken(this, token)
    }
