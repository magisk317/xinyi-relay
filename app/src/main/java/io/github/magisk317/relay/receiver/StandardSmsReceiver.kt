package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.platform.ipc.SmsHookDispatchCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("TooGenericExceptionCaught")
class StandardSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val eventId = SmsHookDispatchCoordinator.ensureIncomingEventId(intent)
        val smsMsg = SmsHookDispatchCoordinator.parseIncomingSms(intent)

        if (smsMsg == null) {
            XLog.e("StandardSmsReceiver: Failed to parse SMS")
            return
        }

        XLog.i("StandardSmsReceiver: Intercepted SMS from %s (eventId=%s)", smsMsg.sender, eventId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prepared = SmsHookDispatchCoordinator.prepareIngressSms(
                    pluginContext = context,
                    phoneContext = context,
                    smsMsg = smsMsg,
                    sourceIntent = intent,
                    eventId = eventId
                )
                if (prepared != null) {
                    SmsHookDispatchCoordinator.dispatchPreparedSms(
                        context = context,
                        prepared = prepared,
                        sentFromUid = null // System broadcasts have no specific calling UID in this context
                    )
                }

            }catch (e: Exception) {
                XLog.e("StandardSmsReceiver: Error dispatching SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
