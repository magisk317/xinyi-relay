package io.github.magisk317.relay.desktop.ui.pages

import io.github.magisk317.relay.contract.model.SnapshotScheduledTask
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScheduledTaskUiLogicTest {

    @Test
    fun `conflict choices preserve only the selected side and restore its status`() {
        val local = conflicted("local", side = "local", previousStatus = 1)
        val remote = conflicted("remote", side = "remote", previousStatus = 0)
        val unrelated = SnapshotScheduledTask(syncId = "other", name = "Other")

        val useLocal = resolveScheduledTaskConflict(listOf(unrelated, local, remote), "conflict-1", ScheduledTaskConflictChoice.LOCAL)
        val useRemote = resolveScheduledTaskConflict(listOf(unrelated, local, remote), "conflict-1", ScheduledTaskConflictChoice.REMOTE)

        assertEquals(listOf("local", "other"), useLocal.map { it.syncId })
        assertEquals(1, useLocal.first { it.syncId == "local" }.status)
        assertEquals(null, useLocal.first { it.syncId == "local" }.conflictId)
        assertEquals(listOf("other", "remote"), useRemote.map { it.syncId })
        assertEquals(0, useRemote.first { it.syncId == "remote" }.status)
    }

    @Test
    fun `keep both drops tombstones and restores previous statuses`() {
        val deletedLocal = conflicted("deleted", side = "local", previousStatus = 1, syncDeleted = true)
        val remote = conflicted("remote", side = "remote", previousStatus = 0)

        val resolved = resolveScheduledTaskConflict(
            listOf(deletedLocal, remote),
            "conflict-1",
            ScheduledTaskConflictChoice.KEEP_BOTH,
        )

        assertEquals(listOf("remote"), resolved.map { it.syncId })
        assertEquals(0, resolved.single().status)
        assertEquals(null, resolved.single().conflictId)
    }

    @Test
    fun `scheduled task editing requires a heartbeat no older than fifteen minutes`() {
        val now = Instant.parse("2026-11-20T12:00:00Z")

        assertTrue(isScheduledTaskHeartbeatEligible(now.minusSeconds(900).toString(), now))
        assertFalse(isScheduledTaskHeartbeatEligible(now.minusSeconds(901).toString(), now))
        assertFalse(isScheduledTaskHeartbeatEligible(null, now))
        assertFalse(isScheduledTaskHeartbeatEligible("not-a-time", now))
    }

    private fun conflicted(
        syncId: String,
        side: String,
        previousStatus: Int,
        syncDeleted: Boolean = false,
    ) = SnapshotScheduledTask(
        syncId = syncId,
        name = syncId,
        status = 0,
        conflictId = "conflict-1",
        conflictSide = side,
        conflictPreviousStatus = previousStatus,
        syncDeleted = syncDeleted,
    )
}
