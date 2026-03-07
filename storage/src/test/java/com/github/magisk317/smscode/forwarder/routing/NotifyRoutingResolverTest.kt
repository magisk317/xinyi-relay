package com.github.magisk317.smscode.forwarder.routing

import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.data.db.dao.NotifyRouteRuleDao
import com.github.magisk317.smscode.data.db.entity.NotifyRouteRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class NotifyRoutingResolverTest {

    @Test
    fun resolve_noRules_keepsLegacyBehavior() {
        val dao = FakeNotifyRouteRuleDao()
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            dao = dao,
        )

        assertEquals(listOf(1L, 2L, 3L), result.senders.map { it.id })
    }

    @Test
    fun resolve_appBinding_onlyBoundSendersRemain() {
        val dao = FakeNotifyRouteRuleDao().apply {
            rules += rule(NotifyRouteScope.APP_ALLOW_SENDER, "com.example.app", 2)
            rules += rule(NotifyRouteScope.APP_ALLOW_SENDER, "com.example.app", 3)
        }
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            dao = dao,
        )

        assertEquals(listOf(2L, 3L), result.senders.map { it.id })
    }

    @Test
    fun resolve_senderWhitelist_rejectsUnlistedApp() {
        val dao = FakeNotifyRouteRuleDao().apply {
            rules += rule(NotifyRouteScope.SENDER_ALLOW_APP, "com.allowed.app", 2)
        }
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.other.app",
            dao = dao,
        )

        assertEquals(listOf(1L, 3L), result.senders.map { it.id })
    }

    @Test
    fun resolve_senderBlacklist_rejectsDeniedSender() {
        val dao = FakeNotifyRouteRuleDao().apply {
            rules += rule(NotifyRouteScope.SENDER_DENY_APP, "com.example.app", 2)
        }
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            dao = dao,
        )

        assertEquals(listOf(1L, 3L), result.senders.map { it.id })
    }

    @Test
    fun resolve_conflict_allowAndDeny_sameSenderDropped() {
        val dao = FakeNotifyRouteRuleDao().apply {
            rules += rule(NotifyRouteScope.SENDER_ALLOW_APP, "com.example.app", 2)
            rules += rule(NotifyRouteScope.SENDER_DENY_APP, "com.example.app", 2)
        }
        val senders = listOf(sender(1), sender(2), sender(3))

        val result = NotifyRoutingResolver.resolveAppNotifySenders(
            candidates = senders,
            packageName = "com.example.app",
            dao = dao,
        )

        assertEquals(listOf(1L, 3L), result.senders.map { it.id })
        assertTrue(2L in result.conflictSenderIds)
    }

    private fun sender(id: Long): Sender = Sender(
        id = id,
        name = "sender$id",
        jsonSetting = "{}",
        time = Date(),
    )

    private fun rule(scope: Int, pkg: String, senderId: Long): NotifyRouteRule = NotifyRouteRule(
        scope = scope,
        packageName = pkg,
        senderId = senderId,
        updateTime = 0L,
    )

    private class FakeNotifyRouteRuleDao : NotifyRouteRuleDao {
        val rules = mutableListOf<NotifyRouteRule>()

        override fun getAll(): List<NotifyRouteRule> = rules.toList()

        override fun getAllFlow(): Flow<List<NotifyRouteRule>> = flowOf(getAll())

        override fun getSenderIdsByScopeAndPackage(scope: Int, packageName: String): List<Long> =
            rules.filter { it.scope == scope && it.packageName == packageName }.map { it.senderId }

        override fun observeSenderIdsByScopeAndPackage(scope: Int, packageName: String): Flow<List<Long>> =
            flowOf(getSenderIdsByScopeAndPackage(scope, packageName))

        override fun getPackageNamesByScopeAndSender(scope: Int, senderId: Long): List<String> =
            rules.filter { it.scope == scope && it.senderId == senderId }.map { it.packageName }

        override fun observePackageNamesByScopeAndSender(scope: Int, senderId: Long): Flow<List<String>> =
            flowOf(getPackageNamesByScopeAndSender(scope, senderId))

        override fun getDistinctSenderIdsByScopeIn(scope: Int, senderIds: List<Long>): List<Long> =
            rules.filter { it.scope == scope && it.senderId in senderIds }
                .map { it.senderId }
                .distinct()

        override fun deleteByScopeAndPackage(scope: Int, packageName: String): Int {
            val before = rules.size
            rules.removeAll { it.scope == scope && it.packageName == packageName }
            return before - rules.size
        }

        override fun deleteByScopeAndSender(scope: Int, senderId: Long): Int {
            val before = rules.size
            rules.removeAll { it.scope == scope && it.senderId == senderId }
            return before - rules.size
        }

        override fun deleteByScopesAndSender(scopes: List<Int>, senderId: Long): Int {
            val before = rules.size
            rules.removeAll { it.scope in scopes && it.senderId == senderId }
            return before - rules.size
        }

        override fun clearAll() {
            rules.clear()
        }

        override fun insert(rule: NotifyRouteRule): Long {
            rules += rule
            return rule.id
        }

        override fun insertAll(rules: List<NotifyRouteRule>) {
            this.rules += rules
        }
    }
}
