package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import io.github.magisk317.smscode.core.utils.XLog
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastPayload
import io.github.magisk317.relay.platform.ipc.ForwardPayloadFactory
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import kotlinx.coroutines.runBlocking

/**
 * Capture SMS code, record it to local DB, and forward to external SmsCode App via Broadcast (IPC).
 */
class ForwardAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val mSmsIntent: Intent? = null,
    private val eventId: String = "",
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {
    private val runtimeRecordFacade = RuntimeRecordFacade(pluginContext)

    override fun action(): Bundle? {
        try {
            val isCodeSms = !mSmsMsg.smsCode.isNullOrBlank()
            XLog.w(
                "Diag ForwardAction: event_id=%s delegate to app process, isCode=%s",
                eventId.ifBlank { "<none>" },
                isCodeSms,
            )
            // Send IPC Broadcast to the integrated SmsCode App Module
            val payload = ForwardPayloadFactory.smsPayload(
                smsMsg = mSmsMsg,
                eventId = eventId.ifBlank { null },
                sourceIntent = mSmsIntent,
            )
            logSimExtras(payload)

            val dispatchResult = ForwardBroadcastDispatcher.dispatchFromSmsHook(
                context = mPluginContext,
                payload = payload,
                sentFromUid = Process.myUid(),
            )
            if (!dispatchResult.dispatched) {
                XLog.e("IPC token is empty, skip forwarding broadcast for security. event_id=%s", eventId.ifBlank { "<none>" })
                persistForwardResult(
                    success = false,
                    target = "SmsCode Engine",
                    message = "IPC token missing",
                )
                return null
            }
            if (dispatchResult.bypassUsed) {
                XLog.w(
                    "IPC token empty, continue forwarding with receiver-side bypass. event_id=%s source=sms_hook uid=%d",
                    eventId.ifBlank { "<none>" },
                    Process.myUid(),
                )
            }

            XLog.i(
                "Successfully broadcasted SMS info to SmsCode Engine (event_id=%s tokenPresent=%s): %s",
                eventId.ifBlank { "<none>" },
                dispatchResult.tokenPresent,
                mSmsMsg.smsCode,
            )
        } catch (t: Throwable) {
            XLog.e("Failed to broadcast SMS info to SmsCode Engine", t)
            persistForwardResult(
                success = false,
                target = "SmsCode Engine",
                message = "IPC Broadcast Failed: ${t.message}"
            )
        }

        return null
    }

    private fun logSimExtras(payload: ForwardBroadcastPayload) {
        XLog.d(
            "ForwardAction SIM extras copied: sim_slot=%s sub_id=%s",
            payload.simSlot?.toString() ?: "N/A",
            payload.subId?.toString() ?: "N/A",
        )
    }

    private fun persistForwardResult(success: Boolean, target: String?, message: String) {
        try {
            runBlocking {
                runtimeRecordFacade.persistSmsForwardResult(
                    smsMsg = mSmsMsg,
                    success = success,
                    target = target,
                    message = message,
                    maxMessageLength = MAX_MESSAGE_LEN,
                )
            }
        } catch (t: Throwable) {
            XLog.w("Persist forward result failed: %s", t.message ?: "unknown")
        }
    }

    companion object {
        private const val MAX_MESSAGE_LEN = 300
    }
}
