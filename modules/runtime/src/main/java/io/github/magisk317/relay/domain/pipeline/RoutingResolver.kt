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
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.xposed.logging.MagiskOtel

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
        var blockedSenders = 0
        val filtered = routedSenders.filter { sender ->
            val decision = ForwardFilterEngine.evaluateSenderScope(
                rules = forwardFilterRules,
                event = event,
                senderId = sender.id,
            )
            if (decision.blocked) {
                blockedSenders += 1
                val senderName = SenderType.displayName(sender.type, sender.name)
                senderFilteredReasonParts += "$senderName:${decision.reason ?: "blocked"}"
                false
            } else {
                true
            }
        }
        if (blockedSenders > 0 || forwardFilterRules.isNotEmpty()) {
            emitFilter(
                stage = "sender_scope",
                decision = ForwardFilterDecision(
                    blocked = blockedSenders > 0 && filtered.isEmpty(),
                    reason = if (blockedSenders == 0) "allowed" else "sender_filtered",
                    allowConfiguredCount = forwardFilterRules.size,
                    allowMatchedCount = filtered.size,
                    denyMatchedCount = blockedSenders,
                ),
                msgType = event.messageType.name,
                targetPackage = event.packageName,
                filteredCount = blockedSenders,
            )
        }
        return SenderRoutingResolution(filtered, routingResult, senderFilteredReasonParts)
    }

    suspend fun evaluatePreRoute(
        event: RelayEvent,
    ): ForwardFilterDecision {
        val decision = when {
            needsFilterEvaluation(event.messageType) -> {
                val rules = db.forwardFilterRuleDao().getEnabledByMsgType(event.messageType.runtimeType).map { it.toDomain() }
                ForwardFilterEngine.evaluatePreRoute(rules, event)
            }
            else -> ForwardFilterDecision(blocked = false)
        }
        emitFilter(
            stage = "pre_route",
            decision = decision,
            msgType = event.messageType.name,
            targetPackage = event.packageName,
        )
        return decision
    }

    private fun emitFilter(
        stage: String,
        decision: ForwardFilterDecision,
        msgType: String,
        targetPackage: String,
        filteredCount: Int? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to if (decision.blocked) "skip" else "ok",
            "duration_ms" to "0",
            "process" to "main",
            "stage" to stage,
            "reason" to (decision.reason ?: if (decision.blocked) "blocked" else "allowed"),
            "msg_type" to msgType,
            "rule_count" to (decision.allowConfiguredCount + decision.denyMatchedCount + decision.allowMatchedCount).toString(),
            "found_count" to decision.allowMatchedCount.toString(),
            "pending_count" to decision.denyMatchedCount.toString(),
        )
        if (targetPackage.isNotBlank()) {
            attrs["target_package"] = targetPackage
        }
        if (filteredCount != null) {
            attrs["change_count"] = filteredCount.toString()
        }
        MagiskOtel.event(name = "sms.block", attributes = attrs, statusOk = true)
    }
}
