package io.github.magisk317.relay.platform.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache

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

    suspend fun dispatchFromHost(
        context: Context,
        payload: ForwardBroadcastPayload,
        orderedAck: ((ForwardBroadcastAck) -> Unit)? = null,
    ) {
        val deps = RuntimeDependencies.get()
        val token = RuntimeSettingsCache.getString(
            key = PrefConst.KEY_IPC_TOKEN,
            defaultValue = "",
        ) { key, defaultValue ->
            deps.preferenceDataSource.getString(key, defaultValue)
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
        @Suppress("UNUSED_PARAMETER") sentFromUid: Int?,
        @Suppress("UNUSED_PARAMETER") sdkInt: Int = android.os.Build.VERSION.SDK_INT,
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
        if (!tokenPresent) {
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
            bypassUsed = false,
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
