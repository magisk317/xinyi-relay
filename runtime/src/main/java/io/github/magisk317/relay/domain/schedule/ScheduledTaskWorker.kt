package io.github.magisk317.relay.domain.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduledTaskWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        XLog.i("ScheduledTaskWorker doWork started")
        val db = AppDatabase.getInstance(applicationContext)
        val activeTasks = db.scheduledTaskDao().getActiveTasks()

        val now = System.currentTimeMillis()
        for (task in activeTasks) {
            // Give 5 minutes window for delayed worker triggers
            if (now >= task.nextRunTime && (now - task.nextRunTime) < 5 * 60 * 1000L) {
                XLog.i("Worker triggering task \${task.id}")
                ScheduledTaskExecutor.executeTask(applicationContext, task.id, "worker")
            }
        }

        // Also re-schedule future tasks to make sure alarms are kept alive
        ScheduledTaskManager(applicationContext, db).scheduleAllActiveTasks()

        Result.success()
    }
}
