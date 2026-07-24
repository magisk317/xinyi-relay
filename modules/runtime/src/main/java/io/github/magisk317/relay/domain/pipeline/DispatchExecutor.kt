package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.engine.service.SenderDispatchResult
import io.github.magisk317.relay.engine.service.SenderDispatcher
import io.github.magisk317.xposed.logging.MagiskOtel

class DispatchExecutor(
    private val delegateProvider: () -> SenderDispatcher,
) : SenderDispatcher {
    override suspend fun dispatchToSender(
        sender: Sender,
        msgInfo: MsgInfo,
        traceId: String?,
    ): SenderDispatchResult {
        val startedAt = System.nanoTime()
        val senderName = SenderType.displayName(sender.type, sender.name)
        XLog.d("Dispatching to sender: id=%d, type=%d, name=%s", sender.id, sender.type, sender.name)
        ForwardFlowLog.d(traceId, "Dispatch sender start name=$senderName type=${sender.type}")
        return runCatching {
            val delegate = delegateProvider()
            delegate.dispatchToSender(sender, msgInfo, traceId)
        }.fold(
            onSuccess = { result ->
                if (result.success) {
                    ForwardFlowLog.i(traceId, "Dispatch sender success name=${result.senderName}")
                } else {
                    ForwardFlowLog.w(traceId, "Dispatch sender failed name=${result.senderName} cause=${result.message}")
                }
                MagiskOtel.event(
                    name = "sms.forward",
                    attributes = mapOf(
                        "result" to if (result.success) "ok" else "error",
                        "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                        "process" to "app",
                        "stage" to "dispatch_executor",
                        "reason" to if (result.success) "success" else "failed",
                        "sender_type" to sender.type.toString(),
                        "event_id_present" to (!traceId.isNullOrBlank()).toString(),
                    ),
                    statusOk = result.success,
                )
                result
            },
            onFailure = { error ->
                val errorSummary = "${error.javaClass.simpleName}: ${error.message ?: "<empty>"}"
                ForwardFlowLog.e(traceId, "Dispatch sender failed name=$senderName cause=$errorSummary", error)
                MagiskOtel.event(
                    name = "sms.forward",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                        "process" to "app",
                        "stage" to "dispatch_executor",
                        "reason" to "exception",
                        "sender_type" to sender.type.toString(),
                        "error_class" to error.javaClass.simpleName,
                        "event_id_present" to (!traceId.isNullOrBlank()).toString(),
                    ),
                    statusOk = false,
                )
                SenderDispatchResult(sender.id, sender.type, senderName, false, errorSummary)
            },
        )
    }
}
