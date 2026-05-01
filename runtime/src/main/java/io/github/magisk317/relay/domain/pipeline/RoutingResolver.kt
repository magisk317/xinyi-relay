package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.engine.event.RelayEvent
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.filter.ForwardFilterDecision
import io.github.magisk317.relay.engine.filter.ForwardFilterEngine
import io.github.magisk317.relay.engine.routing.NotifyRouteRuleReader
import io.github.magisk317.relay.engine.routing.NotifyRoutingResolver
import io.github.magisk317.relay.engine.routing.NotifyRoutingResult
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toDomain

data class SenderRoutingResolution(
    val senders: List<Sender>,
    val routingResult: NotifyRoutingResult? = null,
    val filteredReasonParts: List<String> = emptyList(),
)

class RoutingResolver(private val db: AppDatabase) {
    /** 消息类型是否需要走过滤引擎。 */
    private fun needsFilterEvaluation(messageType: MessageType): Boolean = when (messageType) {
        MessageType.SMS_CODE,
        MessageType.SMS_PLAIN,
        MessageType.APP_NOTIFY,
        MessageType.CALL_NOTIFY,
        -> true
    }

    suspend fun resolve(
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
                val routeRules = object : NotifyRouteRuleReader {
                    override suspend fun getSenderIdsByScopeAndPackage(scope: Int, packageName: String): List<Long> {
                        return db.notifyRouteRuleDao().getSenderIdsByScopeAndPackage(scope, packageName)
                    }

                    override suspend fun getDistinctSenderIdsByScopeIn(scope: Int, senderIds: List<Long>): List<Long> {
                        return db.notifyRouteRuleDao().getDistinctSenderIdsByScopeIn(scope, senderIds)
                    }
                }
                NotifyRoutingResolver.resolveAppNotifySenders(
                    candidates = baseSenders,
                    packageName = event.packageName,
                    rules = routeRules,
                    onConflict = { conflictSenderIds ->
                        ForwardFlowLog.e(
                            traceId,
                            "Notify routing conflict pkg=${event.packageName.trim()} senderIds=$conflictSenderIds (both allow+deny); dropping them",
                        )
                    },
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

    suspend fun evaluatePreRoute(
        event: RelayEvent,
    ): ForwardFilterDecision = when {
        needsFilterEvaluation(event.messageType) -> {
            val rules = db.forwardFilterRuleDao().getEnabledByMsgType(event.messageType.runtimeType).map { it.toDomain() }
            ForwardFilterEngine.evaluatePreRoute(rules, event)
        }
        else -> ForwardFilterDecision(blocked = false)
    }
}
