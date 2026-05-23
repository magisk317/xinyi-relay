package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.service.SenderDispatchResult
import io.github.magisk317.relay.engine.service.SenderDispatcher

class DispatchExecutor(
    private val delegateProvider: () -> SenderDispatcher,
) : SenderDispatcher {
    override suspend fun dispatchToSender(
        sender: Sender,
        msgInfo: MsgInfo,
        traceId: String?,
    ): SenderDispatchResult {
        val senderName = sender.name.ifBlank { "通道${sender.type}" }
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
                result
            },
            onFailure = { error ->
                val errorSummary = "${error.javaClass.simpleName}: ${error.message ?: "<empty>"}"
                ForwardFlowLog.e(traceId, "Dispatch sender failed name=$senderName cause=$errorSummary", error)
                SenderDispatchResult(sender.id, sender.type, senderName, false, errorSummary)
            },
        )
    }
}
