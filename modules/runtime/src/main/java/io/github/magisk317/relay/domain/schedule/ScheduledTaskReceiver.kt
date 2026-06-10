package io.github.magisk317.relay.domain.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduledTaskReceiver : BroadcastReceiver() {
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        XLog.i("ScheduledTaskReceiver onReceive: $action")

        val pendingResult = goAsync()
        val appContext = context.applicationContext ?: context
        receiverScope.launch {
            try {
                runCatching {
                    when (action) {
                        Intent.ACTION_BOOT_COMPLETED,
                        Intent.ACTION_TIME_CHANGED,
                        Intent.ACTION_TIMEZONE_CHANGED -> {
                            val db = AppDatabase.getInstance(appContext)
                            ScheduledTaskManager(appContext, db).scheduleAllActiveTasks()
                        }

                        ScheduledTaskManager.ALARM_ACTION -> {
                            val taskId = intent.getLongExtra(ScheduledTaskManager.EXTRA_TASK_ID, -1L)
                            if (taskId != -1L) {
                                ScheduledTaskExecutor.executeTask(appContext, taskId, "alarm")
                            }
                        }
                    }
                }.onFailure { e ->
                    XLog.e("ScheduledTaskReceiver failed action=$action", e)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
