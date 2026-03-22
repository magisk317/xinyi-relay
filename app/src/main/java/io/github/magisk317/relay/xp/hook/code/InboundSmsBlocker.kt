package io.github.magisk317.relay.xp.hook.code

import android.os.Binder
import io.github.magisk317.smscode.xposed.utils.XLog

internal class InboundSmsBlocker(
    private val smsHandlerClassName: String,
    private val methodInvoker: InboundSmsMethodInvoker = InboundSmsMethodInvoker(smsHandlerClassName),
    private val clearCallingIdentity: () -> Long = Binder::clearCallingIdentity,
    private val restoreCallingIdentity: (Long) -> Unit = Binder::restoreCallingIdentity,
) {
    fun blockInboundSms(
        inboundSmsHandler: Any,
        smsReceiver: Any,
        reason: String,
        eventId: String,
    ) {
        XLog.w("Diag raw-table delete start: reason=%s event_id=%s", reason, eventId)
        val token = clearCallingIdentity()
        try {
            methodInvoker.deleteFromRawTable(inboundSmsHandler, smsReceiver, reason, eventId)
        } catch (e: Throwable) {
            XLog.e("Error occurs when delete SMS data from raw table", e)
        } finally {
            restoreCallingIdentity(token)
        }

        try {
            methodInvoker.sendEventBroadcastComplete(inboundSmsHandler, reason, eventId)
        } catch (e: Throwable) {
            XLog.e("Error occurs when sending broadcast complete", e)
        }
    }
}
