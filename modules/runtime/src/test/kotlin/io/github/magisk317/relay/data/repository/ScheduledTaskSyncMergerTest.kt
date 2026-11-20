package io.github.magisk317.relay.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScheduledTaskSyncMergerTest {

    @Test
    fun sameStableIdChangedOnBothSidesKeepsBothVersionsPaused() {
        val baseTask = ScheduledTaskSyncRecord(
            syncId = "task-a",
            name = "Daily report",
            cronExpression = "0 8 * * *",
            simSlot = 1,
            mobiles = "10086",
            content = "base",
            status = 1,
        )
        val localTask = baseTask.copy(content = "local edit")
        val remoteTask = baseTask.copy(content = "remote edit")

        val merged = ScheduledTaskSyncMerger.merge(
            base = listOf(baseTask),
            local = listOf(localTask),
            remote = listOf(remoteTask),
            mergeKey = "device-7-command-12",
        )

        assertEquals(2, merged.tasks.size)
        assertEquals(setOf("local edit", "remote edit"), merged.tasks.map { it.content }.toSet())
        assertEquals(2, merged.tasks.map { it.syncId }.toSet().size)
        assertTrue(merged.tasks.all { it.status == 0 && it.conflictId != null })
        assertTrue(merged.tasks.all { it.conflictPreviousStatus == 1 })
        assertEquals(setOf("local", "remote"), merged.tasks.map { it.conflictSide }.toSet())
        assertEquals(1, merged.conflicts.size)
        assertEquals(merged.conflicts.single().conflictId, merged.tasks.first().conflictId)
        assertNotEquals(merged.tasks[0].syncId, merged.tasks[1].syncId)

        val retried = ScheduledTaskSyncMerger.merge(
            base = listOf(baseTask),
            local = listOf(localTask),
            remote = listOf(remoteTask),
            mergeKey = "device-7-command-12",
        )
        assertEquals(merged, retried)
    }

    @Test
    fun deleteVersusEditPreservesTombstoneAndEditedTaskUntilResolution() {
        val base = task("task-a", "base")
        val remoteEdit = base.copy(content = "remote")
        val merged = ScheduledTaskSyncMerger.merge(
            base = listOf(base),
            local = emptyList(),
            remote = listOf(remoteEdit),
            mergeKey = "device-7-command-14",
        )

        assertEquals(2, merged.tasks.size)
        assertEquals(1, merged.tasks.count { it.syncDeleted })
        assertEquals(1, merged.tasks.count { !it.syncDeleted })
        assertTrue(merged.tasks.all { it.conflictId == merged.conflicts.single().conflictId && it.status == 0 })

        val chooseLocalDelete = ScheduledTaskSyncMerger.resolve(
            merged.tasks,
            merged.conflicts.single().conflictId,
            ScheduledTaskConflictResolution.USE_LOCAL,
            "device-7-command-14",
        )
        val keepEditedRemote = ScheduledTaskSyncMerger.resolve(
            merged.tasks,
            merged.conflicts.single().conflictId,
            ScheduledTaskConflictResolution.USE_REMOTE,
            "device-7-command-14",
        )
        val keepBoth = ScheduledTaskSyncMerger.resolve(
            merged.tasks,
            merged.conflicts.single().conflictId,
            ScheduledTaskConflictResolution.KEEP_BOTH,
            "device-7-command-14",
        )
        assertEquals(listOf(true), chooseLocalDelete.map { it.syncDeleted })
        assertEquals(listOf("remote"), keepEditedRemote.map { it.content })
        assertEquals(setOf("base", "remote"), keepBoth.map { it.content }.toSet())
        assertTrue(keepBoth.none { it.syncDeleted || it.conflictId != null })
        assertEquals(2, keepBoth.map { it.syncId }.toSet().size)
        assertTrue(keepBoth.none { it.syncId == merged.tasks[0].syncId || it.syncId == merged.tasks[1].syncId })
    }

    @Test
    fun remoteDeleteVersusLocalEditPreservesBothAndResolvesRemoteDeletion() {
        val base = task("task-a", "base")
        val localEdit = base.copy(content = "local")
        val merged = ScheduledTaskSyncMerger.merge(
            base = listOf(base),
            local = listOf(localEdit),
            remote = emptyList(),
            mergeKey = "device-7-command-15",
        )

        assertEquals(2, merged.tasks.size)
        assertEquals(1, merged.tasks.count { it.syncDeleted && it.conflictSide == "remote" })
        assertEquals(1, merged.tasks.count { !it.syncDeleted && it.conflictSide == "local" })
        val resolved = ScheduledTaskSyncMerger.resolve(
            merged.tasks,
            merged.conflicts.single().conflictId,
            ScheduledTaskConflictResolution.USE_REMOTE,
            "device-7-command-15",
        )
        assertEquals(listOf(true), resolved.map { it.syncDeleted })
    }

    @Test
    fun unchangedLocalVersionTakesRemoteEditWithoutConflict() {
        val baseTask = ScheduledTaskSyncRecord(
            syncId = "task-a",
            name = "Daily report",
            cronExpression = "0 8 * * *",
            simSlot = 1,
            mobiles = "10086",
            content = "base",
            status = 1,
        )
        val remoteTask = baseTask.copy(content = "remote edit")

        val merged = ScheduledTaskSyncMerger.merge(
            base = listOf(baseTask),
            local = listOf(baseTask),
            remote = listOf(remoteTask),
            mergeKey = "device-7-command-12",
        )

        assertEquals(listOf(remoteTask), merged.tasks)
        assertTrue(merged.conflicts.isEmpty())
    }

    @Test
    fun differentStableIdsMergeIndependently() {
        val first = task("task-a", "first")
        val second = task("task-b", "second")

        val merged = ScheduledTaskSyncMerger.merge(
            base = listOf(first, second),
            local = listOf(first.copy(content = "local"), second),
            remote = listOf(first, second.copy(content = "remote")),
            mergeKey = "device-7-command-13",
        )

        assertEquals(setOf("local", "remote"), merged.tasks.map { it.content }.toSet())
        assertTrue(merged.conflicts.isEmpty())
    }

    @Test
    fun conflictCanResolveToLocalRemoteOrKeepBoth() {
        val group = listOf(
            task("task-local", "local").pausedForTest("group-a", "local"),
            task("task-remote", "remote").pausedForTest("group-a", "remote"),
        )

        val useLocal = ScheduledTaskSyncMerger.resolve(
            group,
            "group-a",
            ScheduledTaskConflictResolution.USE_LOCAL,
        )
        val useRemote = ScheduledTaskSyncMerger.resolve(
            group,
            "group-a",
            ScheduledTaskConflictResolution.USE_REMOTE,
        )
        val keepBoth = ScheduledTaskSyncMerger.resolve(
            group,
            "group-a",
            ScheduledTaskConflictResolution.KEEP_BOTH,
        )

        assertEquals(listOf("local"), useLocal.map { it.content })
        assertEquals(listOf("remote"), useRemote.map { it.content })
        assertEquals(setOf("local", "remote"), keepBoth.map { it.content }.toSet())
        assertTrue((useLocal + useRemote + keepBoth).all { it.status == 1 && it.conflictId == null })
    }

    private fun task(syncId: String, content: String) = ScheduledTaskSyncRecord(
        syncId = syncId,
        name = "Daily report",
        taskType = "sms",
        cronExpression = "0 8 * * *",
        simSlot = 1,
        mobiles = "10086",
        content = content,
        status = 1,
    )

    private fun ScheduledTaskSyncRecord.pausedForTest(conflictId: String, side: String) = copy(
        status = 0,
        conflictId = conflictId,
        conflictSide = side,
        conflictPreviousStatus = 1,
    )
}
