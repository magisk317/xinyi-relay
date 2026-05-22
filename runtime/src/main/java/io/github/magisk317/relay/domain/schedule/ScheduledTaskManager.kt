package io.github.magisk317.relay.domain.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.entity.ScheduledTaskEntity
import io.github.magisk317.relay.engine.schedule.CronUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ScheduledTaskManager(
    private val context: Context,
    private val appDatabase: AppDatabase
) {
    companion object {
        const val ALARM_ACTION = "io.github.magisk317.relay.action.SCHEDULED_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val WORK_NAME = "ScheduledTaskWorker"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun scheduleAllActiveTasks() {
        withContext(Dispatchers.IO) {
            val tasks = appDatabase.scheduledTaskDao().getActiveTasks()
            tasks.forEach { task ->
                runCatching {
                    scheduleTask(task)
                }.onFailure { e ->
                    XLog.e("Failed to schedule task ${task.id}", e)
                }
            }
            updateFallbackWorker(tasks.isNotEmpty())
        }
    }

    suspend fun rescheduleTask(taskId: Long) {
        withContext(Dispatchers.IO) {
            val task = appDatabase.scheduledTaskDao().getById(taskId)
            if (task != null) {
                if (task.status == 1) {
                    runCatching {
                        scheduleTask(task)
                    }.onFailure { e ->
                        XLog.e("Failed to reschedule task ${task.id}", e)
                        appDatabase.scheduledTaskDao().update(task.apply { nextRunTime = 0L })
                        cancelTask(taskId)
                    }
                } else {
                    cancelTask(taskId)
                }
            }
            refreshFallbackWorkerInternal()
        }
    }

    suspend fun refreshFallbackWorker() {
        withContext(Dispatchers.IO) {
            refreshFallbackWorkerInternal()
        }
    }

    private suspend fun scheduleTask(task: ScheduledTaskEntity) {
        val nextRun = CronUtils.getNextRunTime(task.cronExpression)
        task.nextRunTime = nextRun
        appDatabase.scheduledTaskDao().update(task)

        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = ALARM_ACTION
            data = taskIntentUri(task.id)
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCodeFor(task.id), intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    XLog.w("Cannot schedule exact alarms, lacking permission")
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
            }
            XLog.i("Scheduled task ${task.id} at $nextRun")
        } catch (e: SecurityException) {
            XLog.e("SecurityException scheduling task", e)
        }
    }

    fun cancelTask(taskId: Long) {
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = ALARM_ACTION
            data = taskIntentUri(taskId)
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCodeFor(taskId), intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    private suspend fun refreshFallbackWorkerInternal() {
        val hasActiveTasks = appDatabase.scheduledTaskDao().getActiveTasks().isNotEmpty()
        updateFallbackWorker(hasActiveTasks)
    }

    private fun updateFallbackWorker(hasActiveTasks: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (hasActiveTasks) {
            val workRequest = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(15, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    private fun requestCodeFor(taskId: Long): Int {
        return taskId.hashCode()
    }

    private fun taskIntentUri(taskId: Long): Uri {
        return Uri.parse("xinyi-relay://scheduled-task/$taskId")
    }
}
