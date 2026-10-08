package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import android.os.Process
import io.github.magisk317.relay.xp.hook.SmsForwardConvergence
import io.github.magisk317.relay.xpbridge.PreparedSmsHookDispatch
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.relay.xpbridge.XpMessageTypes
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.runBlocking

internal class ParsedCodeSmsForwarder(
    private val smsForwardPreparer: suspend (Context, Context, SmsMsg, Intent, String) -> PreparedSmsHookDispatch? =
        { resolvedPluginContext, resolvedPhoneContext, smsMsg, intent, eventId ->
            XpDispatchCoordinator.prepareIngressSms(
                pluginContext = resolvedPluginContext,
                phoneContext = resolvedPhoneContext,
                smsMsg = smsMsg,
                sourceIntent = intent,
                eventId = eventId,
            )
        },
    private val smsForwardDispatcher: (Context, PreparedSmsHookDispatch, String) -> Boolean =
        { resolvedPluginContext, prepared, eventId ->
            val dispatchResult = XpDispatchCoordinator.dispatchPreparedSms(
                context = resolvedPluginContext,
                prepared = prepared,
                sentFromUid = Process.myUid(),
            )
            if (!dispatchResult.dispatched) {
                XLog.e(
                    "ParsedCodeSmsForwarder: IPC token empty, skip direct sms forward. event_id=%s",
                    eventId,
                )
                false
            } else {
                if (dispatchResult.bypassUsed) {
                    XLog.w(
                        "ParsedCodeSmsForwarder: IPC token empty, continue with receiver-side bypass. event_id=%s uid=%d",
                        eventId,
                        Process.myUid(),
                    )
                }
                XLog.i(
                    "ParsedCodeSmsForwarder dispatched parsed sms forward: event_id=%s code_present=%s tokenPresent=%s",
                    eventId,
                    prepared.smsMsg.smsCode?.isNotBlank() == true,
                    dispatchResult.tokenPresent,
                )
                true
            }
        },
    private val parsedForwardMarker: (Intent) -> Unit = SmsForwardConvergence::markParsedSmsForwardDispatched,
) {
    fun forwardIfCodeSms(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        sourceIntent: Intent,
        eventId: String,
    ): Boolean {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean, reason: String, codeLength: Int? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
                "stage" to "parsed_code_forward",
                "reason" to reason,
            )
            if (codeLength != null) {
                attrs["code_length"] = codeLength.toString()
            }
            MagiskOtel.event(name = "sms.relay", attributes = attrs, statusOk = statusOk)
        }

        var prepareFailed = false
        val preparedForward = runCatching {
            runBlocking {
                smsForwardPreparer(
                    pluginContext,
                    phoneContext,
                    smsMsg,
                    sourceIntent,
                    eventId,
                )
            }
        }.onFailure { error ->
            prepareFailed = true
            XLog.e("ParsedCodeSmsForwarder failed to prepare direct sms forward", error)
            emit(result = "error", statusOk = false, reason = "prepare_failed")
        }.getOrNull()
        if (preparedForward == null) {
            if (!prepareFailed) {
                emit(result = "skip", statusOk = true, reason = "prepare_null")
            }
            return false
        }

        val hasCode = preparedForward.smsMsg.smsCode?.isNotBlank() == true
        if (!hasCode) {
            emit(result = "skip", statusOk = true, reason = "no_code", codeLength = 0)
            return false
        }
        if (preparedForward.messageType != null && !XpMessageTypes.isSmsCode(preparedForward.messageType)) {
            emit(
                result = "skip",
                statusOk = true,
                reason = "non_code_type",
                codeLength = preparedForward.smsMsg.smsCode?.length,
            )
            return false
        }

        val dispatched = smsForwardDispatcher(pluginContext, preparedForward, eventId)
        if (dispatched) {
            parsedForwardMarker(sourceIntent)
            emit(
                result = "ok",
                statusOk = true,
                reason = "dispatched",
                codeLength = preparedForward.smsMsg.smsCode?.length,
            )
        } else {
            emit(
                result = "error",
                statusOk = false,
                reason = "dispatch_failed",
                codeLength = preparedForward.smsMsg.smsCode?.length,
            )
        }
        return dispatched
    }
}
