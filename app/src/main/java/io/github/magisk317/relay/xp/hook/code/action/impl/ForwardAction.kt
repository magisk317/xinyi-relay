package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.platform.ipc.PreparedSmsHookDispatch
import io.github.magisk317.relay.xp.XpDispatchCoordinator
import io.github.magisk317.relay.xp.XpRecordFacade
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.core.utils.XLog
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
    private val runtimeRecordFacade = XpRecordFacade(pluginContext)

    override fun action(): Bundle? {
        try {
            val isCodeSms = !mSmsMsg.smsCode.isNullOrBlank()
            XLog.w(
                "Diag ForwardAction: event_id=%s delegate to app process, isCode=%s",
                eventId.ifBlank { "<none>" },
                isCodeSms,
            )
            val prepared = XpDispatchCoordinator.prepareParsedSms(
                smsMsg = mSmsMsg,
                eventId = eventId.ifBlank { null },
                sourceIntent = mSmsIntent,
            )
            logSimExtras(prepared)

            val dispatchResult = XpDispatchCoordinator.dispatchPreparedSms(
                context = mPluginContext,
                prepared = prepared,
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

    private fun logSimExtras(prepared: PreparedSmsHookDispatch) {
        XLog.d(
            "ForwardAction SIM extras copied: sim_slot=%s sub_id=%s",
            prepared.payload.simSlot?.toString() ?: "N/A",
            prepared.payload.subId?.toString() ?: "N/A",
        )
    }

    private fun persistForwardResult(success: Boolean, target: String?, message: String) {
        try {
            runBlocking {
                if (success) {
                    runtimeRecordFacade.persistSmsForwardResult(
                        smsMsg = mSmsMsg,
                        success = true,
                        target = target,
                        message = message,
                        maxMessageLength = MAX_MESSAGE_LEN,
                    )
                } else {
                    runtimeRecordFacade.persistSmsHookDispatchFailure(
                        smsMsg = mSmsMsg,
                        message = message,
                        target = target ?: DEFAULT_FORWARD_TARGET,
                        maxMessageLength = MAX_MESSAGE_LEN,
                    )
                }
            }
        } catch (t: Throwable) {
            XLog.w("Persist forward result failed: %s", t.message ?: "unknown")
        }
    }

    companion object {
        private const val MAX_MESSAGE_LEN = 300
        private const val DEFAULT_FORWARD_TARGET = "SmsCode Engine"
    }
}
