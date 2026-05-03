package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.engine.model.ScheduledTask
import kotlinx.coroutines.flow.Flow

interface ScheduledTaskRepository {
    fun getAllTasksFlow(): Flow<List<ScheduledTask>>
    suspend fun getAllTasks(): List<ScheduledTask>
    fun getActiveTasksFlow(): Flow<List<ScheduledTask>>
    suspend fun getActiveTasks(): List<ScheduledTask>
    suspend fun getTaskById(id: Long): ScheduledTask?
    suspend fun insertTask(task: ScheduledTask): Long
    suspend fun updateTask(task: ScheduledTask)
    suspend fun deleteTask(task: ScheduledTask)
    suspend fun scheduleTask(taskId: Long)
    suspend fun cancelTask(taskId: Long)
    suspend fun scheduleAllActiveTasks()
}
