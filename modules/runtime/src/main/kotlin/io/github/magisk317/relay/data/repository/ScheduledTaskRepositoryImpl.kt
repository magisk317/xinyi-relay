package io.github.magisk317.relay.data.repository

import android.content.Context
import androidx.room.withTransaction
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardScheduledTaskEntity
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.domain.schedule.ScheduledTaskManager
import io.github.magisk317.forward.engine.model.ScheduledTask
import io.github.magisk317.forward.engine.service.ScheduledTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ScheduledTaskRepositoryImpl(
    private val context: Context,
    private val database: ForwardDatabase,
    taskManagerOverride: ScheduledTaskManager? = null,
    private val transactionRunner: suspend (suspend () -> Unit) -> Unit = { block ->
        database.withTransaction { block() }
    },
) : ScheduledTaskRepository {

    private val scheduledTaskDao = database.forwardScheduledTaskDao()
    private val taskManager by lazy { taskManagerOverride ?: ScheduledTaskManager(context, database) }

    override fun getAllTasksFlow(): Flow<List<ScheduledTask>> {
        return scheduledTaskDao.getAllFlow().map { tasks -> tasks.map { it.toDomain() } }
    }

    override suspend fun getAllTasks(): List<ScheduledTask> {
        return scheduledTaskDao.getAll().filter { !it.syncDeleted || it.conflictId != null }.map { it.toDomain() }
    }

    override fun getActiveTasksFlow(): Flow<List<ScheduledTask>> {
        return scheduledTaskDao.getActiveTasksFlow().map { tasks -> tasks.map { it.toDomain() } }
    }

    override suspend fun getActiveTasks(): List<ScheduledTask> {
        return scheduledTaskDao.getActiveTasks().map { it.toDomain() }
    }

    override suspend fun getTaskById(id: Long): ScheduledTask? {
        return scheduledTaskDao.getById(id)?.toDomain()
    }

    override suspend fun insertTask(task: ScheduledTask): Long {
        val id = scheduledTaskDao.insert(task.toEntity())
        if (task.status == ScheduledTask.STATUS_ENABLED) {
            taskManager.rescheduleTask(id)
        }
        noteMutation("config.scheduled_task_insert")
        return id
    }

    override suspend fun updateTask(task: ScheduledTask) {
        scheduledTaskDao.update(task.toEntity())
        taskManager.rescheduleTask(task.id)
        noteMutation("config.scheduled_task_update")
    }

    override suspend fun deleteTask(task: ScheduledTask) {
        taskManager.cancelTask(task.id)
        scheduledTaskDao.update(
            task.copy(
                status = ScheduledTask.STATUS_DISABLED,
                syncDeleted = true,
                conflictId = null,
                conflictSide = "",
                conflictPreviousStatus = null,
            ).toEntity(),
        )
        taskManager.refreshFallbackWorker()
        noteMutation("config.scheduled_task_delete")
    }

    override suspend fun resolveSyncConflict(conflictId: String, resolvedTasks: List<ScheduledTask>) {
        val existing = scheduledTaskDao.getByConflictId(conflictId)
        existing.forEach { task -> taskManager.cancelTask(task.id) }
        transactionRunner {
            val current = scheduledTaskDao.getByConflictId(conflictId)
            val currentBySyncId = current.associateBy(ForwardScheduledTaskEntity::syncId)
            val resolvedEntities = resolvedTasks.map { task ->
                val entity = task.toEntity()
                if (entity.id == 0L) entity.copy(id = currentBySyncId[entity.syncId]?.id ?: 0L) else entity
            }
            val resolvedIds = resolvedEntities.mapTo(mutableSetOf(), ForwardScheduledTaskEntity::syncId)
            val tombstones = current.filterNot { it.syncId in resolvedIds }.map { task ->
                task.copy(
                    status = ScheduledTask.STATUS_DISABLED,
                    nextRunTime = 0L,
                    conflictId = null,
                    conflictSide = "",
                    conflictPreviousStatus = null,
                    syncDeleted = true,
                )
            }
            scheduledTaskDao.deleteByConflictId(conflictId)
            scheduledTaskDao.upsertAll(resolvedEntities + tombstones)
        }
        taskManager.scheduleAllActiveTasks()
        noteMutation("config.scheduled_task_conflict_resolved")
    }

    override suspend fun scheduleTask(taskId: Long) {
        taskManager.rescheduleTask(taskId)
    }

    override suspend fun cancelTask(taskId: Long) {
        taskManager.cancelTask(taskId)
    }

    override suspend fun scheduleAllActiveTasks() {
        taskManager.scheduleAllActiveTasks()
    }

    private fun ForwardScheduledTaskEntity.toDomain(): ScheduledTask {
        return ScheduledTask(
            id = id,
            name = name,
            taskType = taskType,
            cronExpression = cronExpression,
            simSlot = simSlot,
            mobiles = mobiles,
            content = content,
            status = status,
            lastRunTime = lastRunTime,
            nextRunTime = nextRunTime,
            createdAt = createdAt,
            syncId = syncId.ifBlank { "legacy-$id" },
            conflictId = conflictId,
            conflictSide = conflictSide,
            conflictPreviousStatus = conflictPreviousStatus,
            syncDeleted = syncDeleted,
        )
    }

    private fun ScheduledTask.toEntity(): ForwardScheduledTaskEntity {
        return ForwardScheduledTaskEntity(
            id = id,
            name = name,
            taskType = taskType,
            cronExpression = cronExpression,
            simSlot = simSlot,
            mobiles = mobiles,
            content = content,
            status = status,
            lastRunTime = lastRunTime,
            nextRunTime = nextRunTime,
            createdAt = createdAt,
            syncId = syncId.ifBlank { UUID.randomUUID().toString() },
            conflictId = conflictId,
            conflictSide = conflictSide,
            conflictPreviousStatus = conflictPreviousStatus,
            syncDeleted = syncDeleted,
        )
    }

    private suspend fun noteMutation(source: String) {
        runCatching { RuntimeDependencies.get().localConfigRepository.noteLocalMutation(source) }
            .onFailure { XLog.e(message = "noteLocalMutation failed: %s", it.message ?: it.javaClass.simpleName) }
        runCatching { RuntimeDependencies.get().autoBackupTrigger.scheduleAutoBackup(source) }
            .onFailure { XLog.e(message = "scheduleAutoBackup failed: %s", it.message ?: it.javaClass.simpleName) }
    }
}
