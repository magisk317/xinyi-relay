package com.github.magisk317.smscode.forwarder.routing

import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.common.utils.ForwardFlowLog
import com.github.magisk317.smscode.data.db.dao.NotifyRouteRuleDao

data class NotifyRoutingResult(
    val senders: List<Sender>,
    val appBoundSenderCount: Int,
    val senderAllowConfiguredCount: Int,
    val senderAllowMatchedCount: Int,
    val senderDenyMatchedCount: Int,
    val conflictSenderIds: Set<Long>,
    val noEligibleReason: String?,
)

object NotifyRoutingResolver {

    fun resolveAppNotifySenders(
        candidates: List<Sender>,
        packageName: String,
        dao: NotifyRouteRuleDao,
        traceId: String? = null,
    ): NotifyRoutingResult {
        val pkg = packageName.trim()
        if (pkg.isEmpty() || candidates.isEmpty()) {
            return NotifyRoutingResult(
                senders = candidates,
                appBoundSenderCount = 0,
                senderAllowConfiguredCount = 0,
                senderAllowMatchedCount = 0,
                senderDenyMatchedCount = 0,
                conflictSenderIds = emptySet(),
                noEligibleReason = null,
            )
        }

        val appBoundSenderIds = dao
            .getSenderIdsByScopeAndPackage(NotifyRouteScope.APP_ALLOW_SENDER, pkg)
            .toSet()
        var stageSenders = if (appBoundSenderIds.isNotEmpty()) {
            candidates.filter { it.id in appBoundSenderIds }
        } else {
            candidates
        }

        if (stageSenders.isEmpty()) {
            return NotifyRoutingResult(
                senders = emptyList(),
                appBoundSenderCount = appBoundSenderIds.size,
                senderAllowConfiguredCount = 0,
                senderAllowMatchedCount = 0,
                senderDenyMatchedCount = 0,
                conflictSenderIds = emptySet(),
                noEligibleReason = if (appBoundSenderIds.isNotEmpty()) {
                    "应用[$pkg]已绑定通道，但绑定目标均不可用（bound=${appBoundSenderIds.size}）"
                } else {
                    null
                },
            )
        }

        val stageIds = stageSenders.map { it.id }
        val senderAllowConfiguredIds = dao
            .getDistinctSenderIdsByScopeIn(NotifyRouteScope.SENDER_ALLOW_APP, stageIds)
            .toSet()
        val senderAllowMatchedIds = dao
            .getSenderIdsByScopeAndPackage(NotifyRouteScope.SENDER_ALLOW_APP, pkg)
            .filter { it in stageIds }
            .toSet()
        val senderDenyMatchedIds = dao
            .getSenderIdsByScopeAndPackage(NotifyRouteScope.SENDER_DENY_APP, pkg)
            .filter { it in stageIds }
            .toSet()
        val conflictSenderIds = senderAllowMatchedIds.intersect(senderDenyMatchedIds)

        if (conflictSenderIds.isNotEmpty()) {
            runCatching {
                ForwardFlowLog.e(
                    traceId,
                    "Notify routing conflict pkg=$pkg senderIds=$conflictSenderIds (both allow+deny); dropping them",
                )
            }
        }

        stageSenders = stageSenders.filter { sender ->
            val senderId = sender.id
            if (senderId in conflictSenderIds) {
                return@filter false
            }
            if (senderId in senderDenyMatchedIds) {
                return@filter false
            }
            if (senderId in senderAllowConfiguredIds && senderId !in senderAllowMatchedIds) {
                return@filter false
            }
            true
        }

        val noEligibleReason = if (stageSenders.isEmpty()) {
            buildString {
                append("应用[$pkg]路由过滤后无可用通道")
                append("（appBound=${appBoundSenderIds.size}")
                append(", senderAllowConfigured=${senderAllowConfiguredIds.size}")
                append(", senderAllowMatched=${senderAllowMatchedIds.size}")
                append(", senderDenyMatched=${senderDenyMatchedIds.size}")
                append(", conflict=${conflictSenderIds.size}）")
            }
        } else {
            null
        }

        return NotifyRoutingResult(
            senders = stageSenders,
            appBoundSenderCount = appBoundSenderIds.size,
            senderAllowConfiguredCount = senderAllowConfiguredIds.size,
            senderAllowMatchedCount = senderAllowMatchedIds.size,
            senderDenyMatchedCount = senderDenyMatchedIds.size,
            conflictSenderIds = conflictSenderIds,
            noEligibleReason = noEligibleReason,
        )
    }
}
