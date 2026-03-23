package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.domain.model.Sender
import io.github.magisk317.relay.domain.filter.ForwardFilterDecision
import io.github.magisk317.relay.domain.filter.ForwardFilterEngine
import io.github.magisk317.relay.domain.routing.NotifyRoutingResolver
import io.github.magisk317.relay.domain.routing.NotifyRoutingResult
import io.github.magisk317.relay.data.mapper.ConfigMapper.toDomain

data class SenderRoutingResolution(
    val senders: List<Sender>,
    val routingResult: NotifyRoutingResult? = null,
    val filteredReasonParts: List<String> = emptyList(),
)

class RoutingResolver(private val db: AppDatabase) {
    /** 消息类型是否需要走过滤引擎（电话提醒走 EventGatekeeper 已经够了） */
    private fun needsFilterEvaluation(messageType: MessageType): Boolean = when (messageType) {
        MessageType.SMS_CODE,
        MessageType.SMS_PLAIN,
        MessageType.APP_NOTIFY,
        -> true
        MessageType.CALL_NOTIFY -> false
    }

    fun resolve(
        baseSenders: List<Sender>,
        event: RelayEvent,
        traceId: String? = null,
    ): SenderRoutingResolution {
        val forwardFilterRules = if (needsFilterEvaluation(event.messageType)) {
            db.forwardFilterRuleDao().getEnabledByMsgType(event.messageType.runtimeType).map { it.toDomain() }
        } else {
            emptyList()
        }
        var routingResult: NotifyRoutingResult? = null
        val routedSenders = if (
            event.messageType == MessageType.APP_NOTIFY &&
            event.packageName.isNotBlank()
        ) {
            runCatching {
                NotifyRoutingResolver.resolveAppNotifySenders(
                    candidates = baseSenders,
                    packageName = event.packageName,
                    dao = db.notifyRouteRuleDao(),
                    traceId = traceId,
                )
            }.getOrNull()?.also { routingResult = it }?.senders ?: baseSenders
        } else {
            baseSenders
        }
        if (forwardFilterRules.isEmpty()) {
            return SenderRoutingResolution(routedSenders, routingResult)
        }
        val senderFilteredReasonParts = mutableListOf<String>()
        val filtered = routedSenders.filter { sender ->
            val decision = ForwardFilterEngine.evaluateSenderScope(
                rules = forwardFilterRules,
                event = event,
                senderId = sender.id,
            )
            if (decision.blocked) {
                senderFilteredReasonParts += "${sender.name.ifBlank { "通道${sender.type}" }}:${decision.reason ?: "blocked"}"
                false
            } else {
                true
            }
        }
        return SenderRoutingResolution(filtered, routingResult, senderFilteredReasonParts)
    }

    fun evaluatePreRoute(
        event: RelayEvent,
    ): ForwardFilterDecision = when {
        needsFilterEvaluation(event.messageType) -> {
            val rules = db.forwardFilterRuleDao().getEnabledByMsgType(event.messageType.runtimeType).map { it.toDomain() }
            ForwardFilterEngine.evaluatePreRoute(rules, event)
        }
        else -> ForwardFilterDecision(blocked = false)
    }
}
