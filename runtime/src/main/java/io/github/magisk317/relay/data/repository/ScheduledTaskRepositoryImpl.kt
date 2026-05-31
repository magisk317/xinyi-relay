package io.github.magisk317.relay.data.repository

import android.content.Context
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.entity.ScheduledTaskEntity
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.schedule.ScheduledTaskManager
import io.github.magisk317.relay.engine.model.ScheduledTask
import io.github.magisk317.relay.engine.service.ScheduledTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScheduledTaskRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase
) : ScheduledTaskRepository {

    private val scheduledTaskDao = database.scheduledTaskDao()
    private val taskManager by lazy { ScheduledTaskManager(context, database) }

    override fun getAllTasksFlow(): Flow<List<ScheduledTask>> {
        return scheduledTaskDao.getAllFlow().map { tasks -> tasks.map { it.toDomain() } }
    }

    override suspend fun getAllTasks(): List<ScheduledTask> {
        return scheduledTaskDao.getAll().map { it.toDomain() }
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
        scheduledTaskDao.delete(task.toEntity())
        taskManager.refreshFallbackWorker()
        noteMutation("config.scheduled_task_delete")
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

    private fun ScheduledTaskEntity.toDomain(): ScheduledTask {
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
        )
    }

    private fun ScheduledTask.toEntity(): ScheduledTaskEntity {
        return ScheduledTaskEntity(
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
        )
    }

    private fun noteMutation(source: String) {
        RuntimeGraph.from(context).autoBackupTrigger.scheduleAutoBackup(source)
    }
}
