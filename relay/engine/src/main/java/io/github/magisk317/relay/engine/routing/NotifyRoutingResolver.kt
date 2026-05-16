package io.github.magisk317.relay.engine.routing

import io.github.magisk317.relay.engine.model.Sender

interface NotifyRouteRuleReader {
    suspend fun getSenderIdsByScopeAndPackage(scope: Int, packageName: String): List<Long>
    suspend fun getDistinctSenderIdsByScopeIn(scope: Int, senderIds: List<Long>): List<Long>
}

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

    suspend fun resolveAppNotifySenders(
        candidates: List<Sender>,
        packageName: String,
        rules: NotifyRouteRuleReader,
        onConflict: ((Set<Long>) -> Unit)? = null,
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

        val appBoundSenderIds = rules
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
        val senderAllowConfiguredIds = rules
            .getDistinctSenderIdsByScopeIn(NotifyRouteScope.SENDER_ALLOW_APP, stageIds)
            .toSet()
        val senderAllowMatchedIds = rules
            .getSenderIdsByScopeAndPackage(NotifyRouteScope.SENDER_ALLOW_APP, pkg)
            .filter { it in stageIds }
            .toSet()
        val senderDenyMatchedIds = rules
            .getSenderIdsByScopeAndPackage(NotifyRouteScope.SENDER_DENY_APP, pkg)
            .filter { it in stageIds }
            .toSet()
        val conflictSenderIds = senderAllowMatchedIds.intersect(senderDenyMatchedIds)

        if (conflictSenderIds.isNotEmpty()) {
            onConflict?.invoke(conflictSenderIds)
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
