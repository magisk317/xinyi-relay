package io.github.magisk317.relay.engine.routing

import io.github.magisk317.relay.engine.model.SenderIdentity
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotifyRoutingResolverTest {

    @Test
    fun resolve_noRules_keepsPreviousBehavior() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(emptyList())
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            rules = reader,
        )

        assertEquals(listOf(1L, 2L, 3L), result.senders.map { it.id })
    }

    @Test
    fun resolve_appBinding_onlyBoundSendersRemain() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(
            listOf(
                routeRule(NotifyRouteScope.APP_ALLOW_SENDER, "com.example.app", 2),
                routeRule(NotifyRouteScope.APP_ALLOW_SENDER, "com.example.app", 3),
            ),
        )
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            rules = reader,
        )

        assertEquals(listOf(2L, 3L), result.senders.map { it.id })
    }

    @Test
    fun resolve_senderWhitelist_rejectsUnlistedApp() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(
            listOf(routeRule(NotifyRouteScope.SENDER_ALLOW_APP, "com.allowed.app", 2)),
        )
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.other.app",
            rules = reader,
        )

        assertEquals(listOf(1L, 3L), result.senders.map { it.id })
    }

    @Test
    fun resolve_senderBlacklist_rejectsDeniedSender() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(
            listOf(routeRule(NotifyRouteScope.SENDER_DENY_APP, "com.example.app", 2)),
        )
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            rules = reader,
        )

        assertEquals(listOf(1L, 3L), result.senders.map { it.id })
    }

    @Test
    fun resolve_conflict_allowAndDeny_sameSenderDropped() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(
            listOf(
                routeRule(NotifyRouteScope.SENDER_ALLOW_APP, "com.example.app", 2),
                routeRule(NotifyRouteScope.SENDER_DENY_APP, "com.example.app", 2),
            ),
        )
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            rules = reader,
        )

        assertEquals(listOf(1L, 3L), result.senders.map { it.id })
        assertTrue(2L in result.conflictSenderIds)
    }

    @Test
    fun resolve_emptyPackageName_keepsAllCandidates() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(
            listOf(routeRule(NotifyRouteScope.APP_ALLOW_SENDER, "com.example.app", 2)),
        )
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "   ",
            rules = reader,
        )

        assertEquals(listOf(1L, 2L, 3L), result.senders.map { it.id })
        assertEquals(0, result.appBoundSenderCount)
    }

    @Test
    fun resolve_emptyCandidates_shortCircuitsBeforeReadingRules() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(
            listOf(routeRule(NotifyRouteScope.APP_ALLOW_SENDER, "com.example.app", 2)),
        )

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = emptyList(),
            packageName = "com.example.app",
            rules = reader,
        )

        assertEquals(emptyList<SenderIdentity>(), result.senders)
        assertEquals(0, result.appBoundSenderCount)
        assertEquals(0, reader.reads)
    }

    @Test
    fun resolve_appBindingWithoutMatch_explainsBoundTargetsUnavailable() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(
            listOf(routeRule(NotifyRouteScope.APP_ALLOW_SENDER, "com.example.app", 9)),
        )
        val senders = listOf(sender(1), sender(2))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            rules = reader,
        )

        assertEquals(emptyList<SenderIdentity>(), result.senders)
        assertEquals(1, result.appBoundSenderCount)
        assertTrue(result.noEligibleReason?.contains("绑定目标均不可用") == true)
    }

    @Test
    fun resolve_allFilteredOut_explainsDroppedCounts() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(
            listOf(routeRule(NotifyRouteScope.SENDER_DENY_APP, "com.example.app", 1)),
        )
        val senders = listOf(sender(1))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            rules = reader,
        )

        assertEquals(emptyList<SenderIdentity>(), result.senders)
        assertEquals(1, result.senderDenyMatchedCount)
        assertTrue(result.noEligibleReason?.contains("无可用通道") == true)
    }

    @Test
    fun resolve_conflict_notifiesCallbackWithConflictIds() = runBlocking {
        val reader = FakeNotifyRouteRuleReader(
            listOf(
                routeRule(NotifyRouteScope.SENDER_ALLOW_APP, "com.example.app", 2),
                routeRule(NotifyRouteScope.SENDER_DENY_APP, "com.example.app", 2),
            ),
        )
        val reported = mutableSetOf<Long>()

        NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = listOf(sender(1), sender(2)),
            packageName = "com.example.app",
            rules = reader,
            onConflict = { reported += it },
        )

        assertEquals(setOf(2L), reported)
    }

    // --- helpers ---

    private data class FakeSender(override val id: Long) : SenderIdentity

    private data class FakeRouteRule(
        val scope: Int,
        val packageName: String,
        val senderId: Long,
    )

    /**
     * 只实现路由解析真正读取的两个查询；[reads] 用于断言空候选没有触发数据库访问。
     */
    private class FakeNotifyRouteRuleReader(rules: List<FakeRouteRule>) : NotifyRouteRuleReader {
        private val rules = rules.toMutableList()
        var reads: Int = 0
            private set

        override suspend fun getSenderIdsByScopeAndPackage(scope: Int, packageName: String): List<Long> {
            reads += 1
            return rules.filter { it.scope == scope && it.packageName == packageName }.map { it.senderId }
        }

        override suspend fun getDistinctSenderIdsByScopeIn(scope: Int, senderIds: List<Long>): List<Long> {
            reads += 1
            return rules.filter { it.scope == scope && it.senderId in senderIds }
                .map { it.senderId }
                .distinct()
        }
    }

    private fun sender(id: Long): FakeSender = FakeSender(id)

    private fun routeRule(scope: Int, pkg: String, senderId: Long): FakeRouteRule = FakeRouteRule(
        scope = scope,
        packageName = pkg,
        senderId = senderId,
    )
}
