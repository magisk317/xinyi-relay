package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.magisk317.relay.platform.ipc.ForwardPayloadFactory
import io.github.magisk317.relay.android.common.utils.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("TooGenericExceptionCaught")
class StandardSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val eventId = ForwardPayloadFactory.ensureSmsEventId(intent)
        val smsMsg = io.github.magisk317.relay.android.data.db.entity.SmsMsg.fromIntent(intent)

        if (smsMsg == null) {
            XLog.e("StandardSmsReceiver: Failed to parse SMS")
            return
        }

        XLog.i("StandardSmsReceiver: Intercepted SMS from %s (eventId=%s)", smsMsg.sender ?: "<null>", eventId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = ForwardPayloadFactory.smsPayload(smsMsg, eventId, intent)
                io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher.dispatchFromHost(
                    context = context,
                    payload = payload
                )
            } catch (e: Throwable) {
                XLog.e("StandardSmsReceiver: Error dispatching SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
