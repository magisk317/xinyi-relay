package io.github.magisk317.relay.domain.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.forward.db.ForwardDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduledTaskWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        XLog.i("ScheduledTaskWorker doWork started")
        val db = ForwardDatabase.build(applicationContext)
        val activeTasks = db.forwardScheduledTaskDao().getActiveTasks()

        val now = System.currentTimeMillis()
        for (task in activeTasks) {
            if (task.nextRunTime <= 0L) {
                XLog.i("Worker repairing unscheduled task ${task.id}")
                ScheduledTaskManager(applicationContext, db).rescheduleTask(task.id)
            } else if (now >= task.nextRunTime) {
                XLog.i("Worker triggering task ${task.id}")
                ScheduledTaskExecutor.executeTask(applicationContext, task.id, "worker")
            }
        }

        // Also re-schedule future tasks to make sure alarms are kept alive
        ScheduledTaskManager(applicationContext, db).scheduleAllActiveTasks()

        Result.success()
    }
}
