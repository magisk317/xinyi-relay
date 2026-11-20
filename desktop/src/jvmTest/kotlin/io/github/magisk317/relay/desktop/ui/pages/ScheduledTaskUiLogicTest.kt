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

        assertEquals(listOf("local", "other", "remote"), useLocal.map { it.syncId })
        assertEquals(1, useLocal.first { it.syncId == "local" }.status)
        assertEquals(null, useLocal.first { it.syncId == "local" }.conflictId)
        assertTrue(useLocal.first { it.syncId == "remote" }.syncDeleted)
        assertEquals(listOf("local", "other", "remote"), useRemote.map { it.syncId })
        assertTrue(useRemote.first { it.syncId == "local" }.syncDeleted)
        assertEquals(0, useRemote.first { it.syncId == "remote" }.status)
    }

    @Test
    fun `remote deletion versus local edit keeps both and remains a tombstone when selected`() {
        val local = conflicted("task-local-copy", side = "local", previousStatus = 1).copy(content = "local edit")
        val remoteDeleted = conflicted("task-original", side = "remote", previousStatus = 1, syncDeleted = true)
            .copy(content = "base definition")
            .copy(content = "base definition")
            .copy(content = "base definition")
            .copy(content = "base definition")
            .copy(content = "base definition")

        val keepBoth = resolveScheduledTaskConflict(
            listOf(local, remoteDeleted),
            "conflict-1",
            ScheduledTaskConflictChoice.KEEP_BOTH,
        )
        val selectRemoteDelete = resolveScheduledTaskConflict(
            listOf(local, remoteDeleted),
            "conflict-1",
            ScheduledTaskConflictChoice.REMOTE,
        )
        val live = keepBoth.filterNot(SnapshotScheduledTask::syncDeleted)

        assertEquals(setOf("base definition", "local edit"), live.map(SnapshotScheduledTask::content).toSet())
        assertEquals(2, live.size)
        assertTrue(live.none { it.syncId == "task-local-copy" || it.syncId == "task-original" })
        assertEquals(2, keepBoth.count(SnapshotScheduledTask::syncDeleted))
        assertTrue(selectRemoteDelete.all(SnapshotScheduledTask::syncDeleted))
    }

    @Test
    fun `keep both preserves both definitions under deterministic fresh ids and keeps old tombstones`() {
        val local = conflicted("local", side = "local", previousStatus = 1)
        val remote = conflicted("remote", side = "remote", previousStatus = 0)
        val tasks = listOf(local, remote)

        val resolved = resolveScheduledTaskConflict(
            tasks,
            "conflict-1",
            ScheduledTaskConflictChoice.KEEP_BOTH,
        )
        val retried = resolveScheduledTaskConflict(tasks, "conflict-1", ScheduledTaskConflictChoice.KEEP_BOTH)
        val live = resolved.filterNot(SnapshotScheduledTask::syncDeleted)
        val tombstones = resolved.filter(SnapshotScheduledTask::syncDeleted)

        assertEquals(resolved, retried)
        assertEquals(2, live.size)
        assertEquals(setOf("local", "remote"), live.map(SnapshotScheduledTask::name).toSet())
        assertEquals(setOf(0, 1), live.map(SnapshotScheduledTask::status).toSet())
        assertTrue(live.none { it.syncId == "local" || it.syncId == "remote" })
        assertEquals(listOf("local", "remote"), tombstones.map(SnapshotScheduledTask::syncId).sorted())
        assertTrue(resolved.all { it.conflictId == null })
    }

    @Test
    fun `keep both delete versus edit restores deleted base as a new task`() {
        val deletedLocal = conflicted("task-original", side = "local", previousStatus = 1, syncDeleted = true)
            .copy(content = "base definition")
        val remote = conflicted("task-remote-copy", side = "remote", previousStatus = 1)
            .copy(content = "remote edit")

        val resolved = resolveScheduledTaskConflict(
            listOf(deletedLocal, remote),
            "conflict-1",
            ScheduledTaskConflictChoice.KEEP_BOTH,
        )
        val live = resolved.filterNot(SnapshotScheduledTask::syncDeleted)

        assertEquals(setOf("base definition", "remote edit"), live.map(SnapshotScheduledTask::content).toSet())
        assertEquals(2, live.size)
        assertTrue(live.none { it.syncId == "task-original" || it.syncId == "task-remote-copy" })
        assertEquals(setOf("task-original", "task-remote-copy"), resolved.filter(SnapshotScheduledTask::syncDeleted).map(SnapshotScheduledTask::syncId).toSet())
    }

    @Test
    fun `remote deletion conflicts with local edit and remains a tombstone when selected`() {
        val local = conflicted("task-local-copy", side = "local", previousStatus = 1).copy(content = "local edit")
        val remoteDeleted = conflicted("task-original", side = "remote", previousStatus = 1, syncDeleted = true)
            .copy(content = "base definition")

        val keepBoth = resolveScheduledTaskConflict(
            listOf(local, remoteDeleted),
            "conflict-1",
            ScheduledTaskConflictChoice.KEEP_BOTH,
        )
        val selectRemoteDelete = resolveScheduledTaskConflict(
            listOf(local, remoteDeleted),
            "conflict-1",
            ScheduledTaskConflictChoice.REMOTE,
        )

        assertEquals(setOf("local edit", "base definition"), keepBoth.filterNot(SnapshotScheduledTask::syncDeleted).map(SnapshotScheduledTask::content).toSet())
        assertEquals(2, keepBoth.count(SnapshotScheduledTask::syncDeleted))
        assertTrue(selectRemoteDelete.single { it.syncId == "task-original" }.syncDeleted)
        assertTrue(selectRemoteDelete.single { it.syncId == "task-local-copy" }.syncDeleted)
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
