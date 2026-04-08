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
            XLog.e("ParsedCodeSmsForwarder failed to prepare direct sms forward", error)
        }.getOrNull() ?: return false

        val hasCode = preparedForward.smsMsg.smsCode?.isNotBlank() == true
        if (!hasCode) {
            return false
        }
        if (preparedForward.messageType != null && !XpMessageTypes.isSmsCode(preparedForward.messageType)) {
            return false
        }

        val dispatched = smsForwardDispatcher(pluginContext, preparedForward, eventId)
        if (dispatched) {
            parsedForwardMarker(sourceIntent)
        }
        return dispatched
    }
}
