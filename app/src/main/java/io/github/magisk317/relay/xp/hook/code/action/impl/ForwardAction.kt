package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.smscode.core.utils.XLog
import io.github.magisk317.relay.data.db.DBProvider
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastPayload
import io.github.magisk317.relay.platform.ipc.ForwardPayloadFactory
import io.github.magisk317.relay.platform.ipc.ForwardReceiverPolicy
import io.github.magisk317.relay.xp.hook.code.action.CallableAction

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

            // Securing IPC with Token: Only the receiver matching our token can process this msg.
            // We use PrefsReader to retrieve token via cross-process Provider.
            val token = PrefsReader.getIpcToken(mPluginContext)
            if (token.isBlank()) {
                if (!ForwardReceiverPolicy.shouldAllowSmsHookTokenBypass(Process.myUid(), Build.VERSION.SDK_INT)) {
                    XLog.e("IPC token is empty, skip forwarding broadcast for security. event_id=%s", eventId.ifBlank { "<none>" })
                    persistForwardResult(
                        success = false,
                        target = "SmsCode Engine",
                        message = "IPC token missing",
                    )
                    return null
                }
                XLog.w(
                    "IPC token empty, continue forwarding with receiver-side bypass. event_id=%s source=sms_hook uid=%d sdk=%d",
                    eventId.ifBlank { "<none>" },
                    Process.myUid(),
                    Build.VERSION.SDK_INT,
                )
            }

            ForwardBroadcastDispatcher.dispatch(
                context = mPluginContext,
                payload = payload,
                token = token.takeIf { it.isNotBlank() },
            )
            
            XLog.i(
                "Successfully broadcasted SMS info to SmsCode Engine (event_id=%s tokenPresent=%s length=%d): %s",
                eventId.ifBlank { "<none>" },
                token.isNotBlank(),
                token.length,
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
        val resolver = mPluginContext.contentResolver
        val smsMsgUri = DBProvider.smsMsgContentUri(mPluginContext)
        val now = System.currentTimeMillis()
        val status = if (success) SmsMsg.FORWARD_STATUS_SUCCESS else SmsMsg.FORWARD_STATUS_FAILED
        val trimmedMessage = message.take(MAX_MESSAGE_LEN)
        val values = ContentValues().apply {
            put("forward_status", status)
            put("forward_target", target)
            put("forward_message", trimmedMessage)
            put("forward_time", now)
        }

        try {
            var matchedId: Long? = null
            val projection = arrayOf("_id", "sender", "body", "date")
            resolver.query(smsMsgUri, projection, null, null, "date DESC")?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val senderIdx = cursor.getColumnIndexOrThrow("sender")
                val bodyIdx = cursor.getColumnIndexOrThrow("body")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(senderIdx)
                    val body = cursor.getString(bodyIdx)
                    val date = cursor.getLong(dateIdx)
                    if (sender == mSmsMsg.sender && body == mSmsMsg.body && date == mSmsMsg.date) {
                        matchedId = cursor.getLong(idIdx)
                        break
                    }
                }
            }

            if (matchedId != null) {
                val itemUri = ContentUris.withAppendedId(smsMsgUri, matchedId)
                resolver.update(itemUri, values, null, null)
            } else {
                values.put("sender", mSmsMsg.sender)
                values.put("body", mSmsMsg.body)
                values.put("date", mSmsMsg.date)
                values.put("company", mSmsMsg.company)
                values.put("sms_code", mSmsMsg.smsCode)
                values.put("package_name", mSmsMsg.packageName)
                resolver.insert(smsMsgUri, values)
            }
        } catch (t: Throwable) {
            XLog.w("Persist forward result failed: %s", t.message ?: "unknown")
        }
    }

    companion object {
        private const val MAX_MESSAGE_LEN = 300
    }
}
