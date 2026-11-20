package io.github.magisk317.relay.data.repository

import java.nio.charset.StandardCharsets
import java.util.UUID

data class ScheduledTaskSyncRecord(
    val syncId: String,
    val name: String,
    val taskType: String = "sms",
    val cronExpression: String,
    val simSlot: Int,
    val mobiles: String,
    val content: String,
    val status: Int,
    val createdAt: Long = 0L,
    val conflictId: String? = null,
    val conflictSide: String = "",
    val conflictPreviousStatus: Int? = null,
    val syncDeleted: Boolean = false,
)

data class ScheduledTaskSyncConflict(
    val conflictId: String,
    val localSyncId: String,
    val remoteSyncId: String,
)

data class ScheduledTaskSyncMerge(
    val tasks: List<ScheduledTaskSyncRecord>,
    val conflicts: List<ScheduledTaskSyncConflict>,
)

enum class ScheduledTaskConflictResolution {
    USE_LOCAL,
    USE_REMOTE,
    KEEP_BOTH,
}

object ScheduledTaskSyncMerger {
    fun merge(
        base: List<ScheduledTaskSyncRecord>,
        local: List<ScheduledTaskSyncRecord>,
        remote: List<ScheduledTaskSyncRecord>,
        mergeKey: String,
    ): ScheduledTaskSyncMerge {
        val baseById = base.associateBy(ScheduledTaskSyncRecord::syncId)
        val localById = local.associateBy(ScheduledTaskSyncRecord::syncId)
        val remoteById = remote.associateBy(ScheduledTaskSyncRecord::syncId)
        val ids = (baseById.keys + localById.keys + remoteById.keys).sorted()
        val merged = mutableListOf<ScheduledTaskSyncRecord>()
        val conflicts = mutableListOf<ScheduledTaskSyncConflict>()

        ids.forEach { syncId ->
            val baseTask = baseById[syncId]
            val localTask = localById[syncId]
            val remoteTask = remoteById[syncId]
            when {
                localTask == baseTask -> remoteTask?.let(merged::add)
                remoteTask == baseTask -> localTask?.let(merged::add)
                localTask == remoteTask -> localTask?.let(merged::add)
                localTask != null && remoteTask != null -> {
                    val conflictId = stableId(mergeKey, syncId, "conflict")
                    val localConflict = localTask.paused(conflictId, "local")
                    val remoteConflict = remoteTask.copy(
                        syncId = stableId(mergeKey, syncId, "remote"),
                    ).paused(conflictId, "remote")
                    merged += localConflict
                    merged += remoteConflict
                    conflicts += ScheduledTaskSyncConflict(
                        conflictId = conflictId,
                        localSyncId = localConflict.syncId,
                        remoteSyncId = remoteConflict.syncId,
                    )
                }
                localTask == null && remoteTask != null && baseTask != null -> {
                    val conflictId = stableId(mergeKey, syncId, "delete-local")
                    val tombstone = baseTask.copy(
                        syncId = syncId,
                        syncDeleted = true,
                    ).paused(conflictId, "local")
                    val edited = remoteTask.copy(
                        syncId = stableId(mergeKey, syncId, "remote"),
                    ).paused(conflictId, "remote")
                    merged += tombstone
                    merged += edited
                    conflicts += ScheduledTaskSyncConflict(
                        conflictId = conflictId,
                        localSyncId = tombstone.syncId,
                        remoteSyncId = edited.syncId,
                    )
                }
                localTask != null && remoteTask == null && baseTask != null -> {
                    val conflictId = stableId(mergeKey, syncId, "delete-remote")
                    val edited = localTask.copy(
                        syncId = stableId(mergeKey, syncId, "local"),
                    ).paused(conflictId, "local")
                    val tombstone = baseTask.copy(
                        syncId = syncId,
                        syncDeleted = true,
                    ).paused(conflictId, "remote")
                    merged += edited
                    merged += tombstone
                    conflicts += ScheduledTaskSyncConflict(
                        conflictId = conflictId,
                        localSyncId = edited.syncId,
                        remoteSyncId = tombstone.syncId,
                    )
                }
                else -> (localTask ?: remoteTask)?.let(merged::add)
            }
        }
        return ScheduledTaskSyncMerge(merged.sortedBy(ScheduledTaskSyncRecord::syncId), conflicts)
    }

    fun resolve(
        tasks: List<ScheduledTaskSyncRecord>,
        conflictId: String,
        resolution: ScheduledTaskConflictResolution,
        mergeKey: String = conflictId,
    ): List<ScheduledTaskSyncRecord> {
        val group = tasks.filter { it.conflictId == conflictId }
        require(group.isNotEmpty()) { "scheduled task conflict not found" }
        val retained = when (resolution) {
            ScheduledTaskConflictResolution.USE_LOCAL -> group.filter { it.conflictSide == "local" }.take(1)
            ScheduledTaskConflictResolution.USE_REMOTE -> group.filter { it.conflictSide == "remote" }.take(1)
            ScheduledTaskConflictResolution.KEEP_BOTH -> group
        }
        val resolvedRetained = retained.map { task ->
            val status = task.conflictPreviousStatus ?: ScheduledTaskStatus.ENABLED
            if (resolution == ScheduledTaskConflictResolution.KEEP_BOTH && task.syncDeleted) {
                task.copy(
                    syncId = stableId(mergeKey, conflictId, "keep-both-delete-${task.conflictSide}"),
                    status = status,
                    syncDeleted = false,
                    conflictId = null,
                    conflictSide = "",
                    conflictPreviousStatus = null,
                )
            } else {
                task.copy(
                    status = if (task.syncDeleted) ScheduledTaskStatus.DISABLED else status,
                    conflictId = null,
                    conflictSide = "",
                    conflictPreviousStatus = null,
                )
            }
        }
        require(resolvedRetained.isNotEmpty()) { "scheduled task conflict side not found" }
        val resolvedIds = group.mapTo(mutableSetOf(), ScheduledTaskSyncRecord::syncId)
        return (tasks.filterNot { it.syncId in resolvedIds } + resolvedRetained).sortedBy(ScheduledTaskSyncRecord::syncId)
    }

    private fun ScheduledTaskSyncRecord.paused(
        conflictId: String,
        side: String,
    ): ScheduledTaskSyncRecord = copy(
        status = ScheduledTaskStatus.DISABLED,
        conflictId = conflictId,
        conflictSide = side,
        conflictPreviousStatus = conflictPreviousStatus ?: status,
    )

    private fun stableId(mergeKey: String, syncId: String, role: String): String =
        UUID.nameUUIDFromBytes("$mergeKey:$syncId:$role".toByteArray(StandardCharsets.UTF_8)).toString()
}

private object ScheduledTaskStatus {
    const val ENABLED = 1
    const val DISABLED = 0
}
