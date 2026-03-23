package io.github.magisk317.relay.platform.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.prefs.PrefsReader
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import kotlinx.coroutines.runBlocking

data class ForwardBroadcastAck(
    val resultCode: Int,
    val resultData: String?,
    val resultExtras: Bundle?,
)

data class SmsHookDispatchResult(
    val dispatched: Boolean,
    val tokenPresent: Boolean,
    val bypassUsed: Boolean,
)

object ForwardBroadcastDispatcher {
    fun dispatch(
        context: Context,
        payload: ForwardBroadcastPayload,
        token: String? = null,
        orderedAck: ((ForwardBroadcastAck) -> Unit)? = null,
    ) {
        val intent = payload.toIntent(
            context = context,
            token = token.takeIf { !it.isNullOrBlank() },
        )
        dispatchIntent(context, intent, orderedAck)
    }

    fun dispatchFromHost(
        context: Context,
        payload: ForwardBroadcastPayload,
        orderedAck: ((ForwardBroadcastAck) -> Unit)? = null,
    ) {
        val runtimeGraph = RuntimeGraph.from(context)
        val token = runBlocking {
            RuntimeSettingsCache.getString(
                key = PrefConst.KEY_IPC_TOKEN,
                defaultValue = "",
            ) { key, defaultValue ->
                runtimeGraph.preferenceDataSource.getString(key, defaultValue)
            }
        }
        dispatch(
            context = context,
            payload = payload,
            token = token,
            orderedAck = orderedAck,
        )
    }

    fun dispatchFromSmsHook(
        context: Context,
        payload: ForwardBroadcastPayload,
        sentFromUid: Int?,
        sdkInt: Int = android.os.Build.VERSION.SDK_INT,
        tokenResolver: (Context) -> String = PrefsReader::getIpcToken,
        dispatchBlock: (String?) -> Unit = { resolvedToken ->
            dispatch(
                context = context,
                payload = payload,
                token = resolvedToken,
            )
        },
    ): SmsHookDispatchResult {
        val token = tokenResolver(context)
        val tokenPresent = token.isNotBlank()
        val bypassUsed = !tokenPresent &&
            ForwardReceiverPolicy.shouldAllowSmsHookTokenBypass(
                sentFromUid = sentFromUid,
                sdkInt = sdkInt,
            )
        if (!tokenPresent && !bypassUsed) {
            return SmsHookDispatchResult(
                dispatched = false,
                tokenPresent = false,
                bypassUsed = false,
            )
        }
        dispatchBlock(token.takeIf { tokenPresent })
        return SmsHookDispatchResult(
            dispatched = true,
            tokenPresent = tokenPresent,
            bypassUsed = bypassUsed,
        )
    }

    private fun dispatchIntent(
        context: Context,
        intent: Intent,
        orderedAck: ((ForwardBroadcastAck) -> Unit)?,
    ) {
        if (orderedAck == null) {
            context.sendBroadcast(intent)
            return
        }
        context.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    orderedAck(
                        ForwardBroadcastAck(
                            resultCode = resultCode,
                            resultData = resultData,
                            resultExtras = getResultExtras(true),
                        ),
                    )
                }
            },
            null,
            0,
            null,
            null,
        )
    }
}
