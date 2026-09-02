@file:Suppress("TooGenericExceptionCaught")

package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.magisk317.relay.android.common.utils.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StandardMmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION,
                Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION,
            )
        ) return
        if (!StandardMessageIngressHandler.isMmsWapPush(intent)) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (StandardMessageIngressHandler.shouldHandleStandardMode(context, "StandardMmsReceiver")) {
                    StandardMessageIngressHandler.dispatchMms(context, intent)
                }
            } catch (error: Throwable) {
                XLog.e("StandardMmsReceiver: Error dispatching MMS", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
