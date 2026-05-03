package io.github.magisk317.relay.domain.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.entity.ScheduledTaskEntity
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
                scheduleTask(task)
            }

            // Setup fallback worker
            if (tasks.isNotEmpty()) {
                val workRequest = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(15, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }
        }
    }

    suspend fun rescheduleTask(taskId: Long) {
        withContext(Dispatchers.IO) {
            val task = appDatabase.scheduledTaskDao().getById(taskId)
            if (task != null) {
                if (task.status == 1) {
                    scheduleTask(task)
                } else {
                    cancelTask(taskId)
                }
            }
        }
    }

    private suspend fun scheduleTask(task: ScheduledTaskEntity) {
        val nextRun = CronUtils.getNextRunTime(task.cronExpression)
        task.nextRunTime = nextRun
        appDatabase.scheduledTaskDao().update(task)

        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = ALARM_ACTION
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, task.id.toInt(), intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    XLog.w("Cannot schedule exact alarms, lacking permission")
                    // Fallback to exact or inexact
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
            }
            XLog.i("Scheduled task \${task.id} at \$nextRun")
        } catch (e: SecurityException) {
            XLog.e("SecurityException scheduling task", e)
        }
    }

    fun cancelTask(taskId: Long) {
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = ALARM_ACTION
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, taskId.toInt(), intent, flags)
        alarmManager.cancel(pendingIntent)
    }
}
