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
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.entity.ScheduledTaskEntity
import io.github.magisk317.relay.engine.schedule.CronUtils
import io.github.magisk317.relay.runtime.BuildConfig
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
        private const val NANOS_PER_MILLI = 1_000_000L
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun scheduleAllActiveTasks() {
        withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            val tasks = appDatabase.scheduledTaskDao().getActiveTasks()
            if (!BuildConfig.ENABLE_SMS_CHANNEL) {
                tasks.forEach { task -> cancelTask(task.id) }
                updateFallbackWorker(false)
                XLog.w("Scheduled SMS tasks disabled in current distribution, active task alarms cancelled")
                MagiskOtel.event(
                    name = "sms.schedule",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to elapsedMs(startedAt).toString(),
                        "process" to "main",
                        "stage" to "schedule_all",
                        "reason" to "sms_channel_disabled",
                        "found_count" to tasks.size.toString(),
                    ),
                    statusOk = true,
                )
                return@withContext
            }
            var failed = 0
            tasks.forEach { task ->
                runCatching {
                    scheduleTask(task)
                }.onFailure { e ->
                    failed += 1
                    XLog.e("Failed to schedule task ${task.id}", e)
                }
            }
            updateFallbackWorker(tasks.isNotEmpty())
            MagiskOtel.event(
                name = "sms.schedule",
                attributes = mapOf(
                    "result" to if (failed == 0) "ok" else if (failed < tasks.size) "ok" else "error",
                    "duration_ms" to elapsedMs(startedAt).toString(),
                    "process" to "main",
                    "stage" to "schedule_all",
                    "reason" to if (failed == 0) "scheduled" else "partial_failed",
                    "found_count" to tasks.size.toString(),
                    "pending_count" to failed.toString(),
                ),
                statusOk = failed < tasks.size || tasks.isEmpty(),
            )
        }
    }

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)

    suspend fun rescheduleTask(taskId: Long) {
        withContext(Dispatchers.IO) {
            if (!BuildConfig.ENABLE_SMS_CHANNEL) {
                cancelTask(taskId)
                updateFallbackWorker(false)
                XLog.w("ScheduledTask $taskId not rescheduled: SMS channel disabled in current distribution")
                return@withContext
            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                XLog.w("Cannot schedule exact alarms, lacking permission")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
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
        if (!BuildConfig.ENABLE_SMS_CHANNEL) {
            updateFallbackWorker(false)
            return
        }
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
