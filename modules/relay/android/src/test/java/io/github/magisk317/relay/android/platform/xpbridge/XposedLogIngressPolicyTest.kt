package io.github.magisk317.relay.android.platform.xpbridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XposedLogIngressPolicyTest {
    @Test
    fun `persistent quota enforces count and bytes across instances`() {
        val store = MemoryQuotaStore()
        var now = 2L * DAY_MILLIS
        val first = quota(store, maxEvents = 2, maxBytes = 10, now = { now })

        assertTrue(first.tryConsume(uid = 1_000, bytes = 4))
        val restored = quota(store, maxEvents = 2, maxBytes = 10, now = { now })
        assertTrue(restored.tryConsume(uid = 1_000, bytes = 6))
        assertFalse(restored.tryConsume(uid = 1_000, bytes = 0))
        assertFalse(restored.tryConsume(uid = 1_001, bytes = 11))
        assertTrue(restored.tryConsume(uid = 1_001, bytes = 10))

        now += DAY_MILLIS
        assertTrue(restored.tryConsume(uid = 1_000, bytes = 10))
    }

    @Test
    fun `clock rollback does not reset persisted quota`() {
        val store = MemoryQuotaStore()
        var now = 3L * DAY_MILLIS
        val quota = quota(store, maxEvents = 1, maxBytes = 10, now = { now })

        assertTrue(quota.tryConsume(uid = 1_000, bytes = 1))
        now -= DAY_MILLIS
        assertFalse(quota.tryConsume(uid = 1_000, bytes = 1))
    }

    @Test
    fun `quota storage failures deny the event`() {
        val readFailure = object : UidQuotaStore {
            override fun read(uid: Int): UidQuotaSnapshot = error("read failed")
            override fun write(uid: Int, snapshot: UidQuotaSnapshot): Boolean = true
        }
        val writeFailure = object : UidQuotaStore {
            override fun read(uid: Int): UidQuotaSnapshot? = null
            override fun write(uid: Int, snapshot: UidQuotaSnapshot): Boolean = false
        }

        assertFalse(quota(readFailure, 1, 1) { 0L }.tryConsume(1_000, 1))
        assertFalse(quota(writeFailure, 1, 1) { 0L }.tryConsume(1_000, 1))
        assertFalse(quota(writeFailure, 1, 1) { 0L }.tryConsume(-1, 1))
        assertFalse(quota(writeFailure, 1, 1) { -1L }.tryConsume(1_000, 1))
    }

    private fun quota(
        store: UidQuotaStore,
        maxEvents: Int,
        maxBytes: Long,
        now: () -> Long,
    ) = PersistentUidQuota(store, maxEvents, maxBytes, now)

    private class MemoryQuotaStore : UidQuotaStore {
        private val snapshots = mutableMapOf<Int, UidQuotaSnapshot>()

        override fun read(uid: Int): UidQuotaSnapshot? = snapshots[uid]

        override fun write(uid: Int, snapshot: UidQuotaSnapshot): Boolean {
            snapshots[uid] = snapshot
            return true
        }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
