package io.github.magisk317.relay.desktop.ui.pages

import io.github.magisk317.relay.contract.model.SnapshotScheduledTask
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class ScheduledTaskConflictChoice { LOCAL, REMOTE, KEEP_BOTH }

fun resolveScheduledTaskConflict(
    tasks: List<SnapshotScheduledTask>,
    conflictId: String,
    choice: ScheduledTaskConflictChoice,
): List<SnapshotScheduledTask> {
    val group = tasks.filter { it.conflictId == conflictId }
    require(group.isNotEmpty()) { "scheduled task conflict not found" }

    val resolved = when (choice) {
        ScheduledTaskConflictChoice.KEEP_BOTH -> {
            val copies = group.map { task ->
                task.copy(
                    syncId = deterministicResolvedSyncId(conflictId, task),
                    status = task.conflictPreviousStatus ?: ENABLED_STATUS,
                    conflictId = null,
                    conflictSide = "",
                    conflictPreviousStatus = null,
                    syncDeleted = false,
                )
            }
            copies + group.map(::tombstoneConflictVersion)
        }
        ScheduledTaskConflictChoice.LOCAL,
        ScheduledTaskConflictChoice.REMOTE -> {
            val selectedSide = if (choice == ScheduledTaskConflictChoice.LOCAL) "local" else "remote"
            val selected = group.firstOrNull { it.conflictSide == selectedSide }
                ?: throw IllegalArgumentException("scheduled task conflict choice has no retained task")
            listOf(
                selected.copy(
                    status = if (selected.syncDeleted) DISABLED_STATUS else selected.conflictPreviousStatus ?: ENABLED_STATUS,
                    conflictId = null,
                    conflictSide = "",
                    conflictPreviousStatus = null,
                ),
            ) + group.filterNot { it.syncId == selected.syncId }.map(::tombstoneConflictVersion)
        }
    }
    return (tasks.filterNot { it.conflictId == conflictId } + resolved).sortedBy(SnapshotScheduledTask::syncId)
}

private fun tombstoneConflictVersion(task: SnapshotScheduledTask) = task.copy(
    status = DISABLED_STATUS,
    conflictId = null,
    conflictSide = "",
    conflictPreviousStatus = null,
    syncDeleted = true,
)

private fun deterministicResolvedSyncId(conflictId: String, task: SnapshotScheduledTask): String =
    UUID.nameUUIDFromBytes(
        "xinyi-task-resolution:$conflictId:${task.syncId}:${task.conflictSide}".toByteArray(StandardCharsets.UTF_8),
    ).toString()

fun isScheduledTaskHeartbeatEligible(lastSeenAt: String?, now: Instant): Boolean {
    val lastSeen = lastSeenAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    return Duration.between(lastSeen, now) <= HEARTBEAT_MAX_AGE
}

private val HEARTBEAT_MAX_AGE: Duration = Duration.ofMinutes(15)
private const val ENABLED_STATUS = 1
private const val DISABLED_STATUS = 0
