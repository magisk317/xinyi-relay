package io.github.magisk317.relay.legacy.forwarder

import android.content.Context
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.android.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.engine.event.RelayEvent
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.engine.model.MsgInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Phase3 complete: keep this file as a thin legacy bridge only.
// New callers must emit RelayEvent directly; SendUtils/LegacyRelayFacade remain for compatibility.
object SendUtils {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendMsg(
        context: Context,
        msgInfo: MsgInfo,
        messageType: MessageType,
        recordId: Long? = null,
        traceId: String? = null,
    ) {
        scope.launch {
            runCatching {
                LegacyRelayFacade.dispatch(
                    context = context,
                    msgInfo = msgInfo,
                    messageType = messageType,
                    recordId = recordId,
                    traceId = traceId,
                )
            }.onFailure { error ->
                XLog.e("SendUtils facade dispatch failed", error)
                ForwardFlowLog.e(traceId, "SendUtils facade dispatch failed", error)
            }
        }
    }

    fun sendMsgToSenderIds(
        context: Context,
        msgInfo: MsgInfo,
        senderIds: List<Long>,
        messageType: MessageType,
        traceId: String? = null,
    ) {
        if (senderIds.isEmpty()) return
        scope.launch {
            runCatching {
                LegacyRelayFacade.dispatchToSenderIds(
                    context = context,
                    msgInfo = msgInfo,
                    senderIds = senderIds,
                    messageType = messageType,
                    traceId = traceId,
                )
            }.onFailure { error ->
                XLog.e("Direct dispatch failed", error)
                ForwardFlowLog.e(traceId, "Direct dispatch failed", error)
            }
        }
    }

    // Legacy entrypoints must pass explicit MessageType; no inference is allowed here.
}

// Phase3 complete: legacy callers must determine MessageType before entering the runtime pipeline.
object LegacyRelayFacade {
    suspend fun dispatch(
        context: Context,
        msgInfo: MsgInfo,
        messageType: MessageType,
        recordId: Long? = null,
        traceId: String? = null,
    ) {
        val event = msgInfo.toLegacyRelayEvent(messageType, sourceType = "legacy_send_utils")
        RuntimeGraph.from(context).eventPipeline.process(
            event = event,
            preferredRecordId = recordId,
            traceId = traceId ?: "legacy_${event.sourceType}_${event.timestamp}",
        )
    }

    suspend fun dispatchToSenderIds(
        context: Context,
        msgInfo: MsgInfo,
        senderIds: List<Long>,
        messageType: MessageType,
        traceId: String? = null,
    ) {
        val event = msgInfo.toLegacyRelayEvent(messageType, sourceType = "legacy_send_utils").copy(targetSenderIds = senderIds)
        RuntimeGraph.from(context).eventPipeline.process(
            event = event,
            traceId = traceId ?: "legacy_targeted_${event.sourceType}_${event.timestamp}",
        )
    }

    fun toRelayEvent(
        msgInfo: MsgInfo,
        messageType: MessageType,
        sourceType: String = "legacy_send_utils",
    ): RelayEvent = msgInfo.toLegacyRelayEvent(messageType, sourceType)

    private fun MsgInfo.toLegacyRelayEvent(
        messageType: MessageType,
        sourceType: String,
    ): RelayEvent {
        return RelayEvent(
            messageType = messageType,
            sourceType = sourceType,
            sender = from,
            body = content,
            timestamp = date.time,
            packageName = packageName,
            notifyChannelId = notifyChannelId,
            companyOrAppName = simInfo,
            smsCode = null,
            callType = callType,
            callStage = "",
            simSlot = simSlot,
            subId = subId,
            contactName = contactName,
            phoneArea = phoneArea,
        )
    }
}
