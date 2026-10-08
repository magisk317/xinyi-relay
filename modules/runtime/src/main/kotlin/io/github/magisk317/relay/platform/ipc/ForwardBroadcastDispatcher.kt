package io.github.magisk317.relay.platform.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.xposed.logging.MagiskOtel

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
        val startedAt = System.nanoTime()
        val intent = payload.toIntent(
            context = context,
            token = token.takeIf { !it.isNullOrBlank() },
        )
        runCatching {
            dispatchIntent(context, intent, orderedAck)
        }.fold(
            onSuccess = {
                emitRelay(
                    startedAt = startedAt,
                    result = "ok",
                    reason = if (orderedAck != null) "ordered" else "unordered",
                    msgType = payload.msgType,
                    source = payload.forwardSource,
                )
            },
            onFailure = { error ->
                emitRelay(
                    startedAt = startedAt,
                    result = "error",
                    statusOk = false,
                    reason = error.javaClass.simpleName,
                    msgType = payload.msgType,
                    source = payload.forwardSource,
                )
                throw error
            },
        )
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
        val startedAt = System.nanoTime()
        val token = tokenResolver(context)
        val tokenPresent = token.isNotBlank()
        if (!tokenPresent) {
            emitRelay(
                startedAt = startedAt,
                result = "skip",
                reason = "token_missing",
                msgType = payload.msgType,
                source = payload.forwardSource,
                process = "hook",
            )
            return SmsHookDispatchResult(
                dispatched = false,
                tokenPresent = false,
                bypassUsed = false,
            )
        }
        runCatching {
            dispatchBlock(token.takeIf { tokenPresent })
        }.fold(
            onSuccess = {
                emitRelay(
                    startedAt = startedAt,
                    result = "ok",
                    reason = "hook_dispatch",
                    msgType = payload.msgType,
                    source = payload.forwardSource,
                    process = "hook",
                )
            },
            onFailure = { error ->
                emitRelay(
                    startedAt = startedAt,
                    result = "error",
                    statusOk = false,
                    reason = error.javaClass.simpleName,
                    msgType = payload.msgType,
                    source = payload.forwardSource,
                    process = "hook",
                )
                throw error
            },
        )
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

    private fun emitRelay(
        startedAt: Long,
        result: String,
        statusOk: Boolean = true,
        reason: String? = null,
        msgType: String? = null,
        source: String? = null,
        process: String = "main",
    ) {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to process,
        )
        if (reason != null) attrs["reason"] = reason
        if (!msgType.isNullOrBlank()) attrs["msg_type"] = msgType
        if (!source.isNullOrBlank()) attrs["source"] = source
        MagiskOtel.event(name = "sms.relay", attributes = attrs, statusOk = statusOk)
    }
}
