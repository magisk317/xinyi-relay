package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.magisk317.relay.android.common.utils.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("TooGenericExceptionCaught")
class StandardSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!StandardMessageIngressHandler.isSmsReceived(intent)) return
        if (!StandardMessageIngressHandler.shouldHandleStandardMode(context, "StandardSmsReceiver")) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                StandardMessageIngressHandler.dispatchSms(context, intent)
            } catch (error: Throwable) {
                XLog.e("StandardSmsReceiver: Error dispatching SMS", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
