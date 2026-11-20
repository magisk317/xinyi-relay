package io.github.magisk317.relay.desktop.ui.pages

import io.github.magisk317.relay.contract.model.SnapshotScheduledTask
import java.time.Duration
import java.time.Instant

enum class ScheduledTaskConflictChoice { LOCAL, REMOTE, KEEP_BOTH }

fun resolveScheduledTaskConflict(
    tasks: List<SnapshotScheduledTask>,
    conflictId: String,
    choice: ScheduledTaskConflictChoice,
): List<SnapshotScheduledTask> {
    val group = tasks.filter { it.conflictId == conflictId }
    require(group.isNotEmpty()) { "scheduled task conflict not found" }

    val retained = when (choice) {
        ScheduledTaskConflictChoice.LOCAL -> group.filter { it.conflictSide == "local" }.take(1)
        ScheduledTaskConflictChoice.REMOTE -> group.filter { it.conflictSide == "remote" }.take(1)
        ScheduledTaskConflictChoice.KEEP_BOTH -> group.filterNot(SnapshotScheduledTask::syncDeleted)
    }
    require(retained.isNotEmpty()) { "scheduled task conflict choice has no retained task" }

    val resolved = retained.map { task ->
        task.copy(
            status = task.conflictPreviousStatus ?: ENABLED_STATUS,
            conflictId = null,
            conflictSide = "",
            conflictPreviousStatus = null,
        )
    }
    return (tasks.filterNot { it.conflictId == conflictId } + resolved).sortedBy(SnapshotScheduledTask::syncId)
}

fun isScheduledTaskHeartbeatEligible(lastSeenAt: String?, now: Instant): Boolean {
    val lastSeen = lastSeenAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    return Duration.between(lastSeen, now) <= HEARTBEAT_MAX_AGE
}

private val HEARTBEAT_MAX_AGE: Duration = Duration.ofMinutes(15)
private const val ENABLED_STATUS = 1
