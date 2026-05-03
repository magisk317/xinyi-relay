package io.github.magisk317.relay.domain.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.android.common.utils.XLog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ScheduledTaskReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        XLog.i("ScheduledTaskReceiver onReceive: \$action")

        val pendingResult = goAsync()
        GlobalScope.launch {
            try {
                if (action == Intent.ACTION_BOOT_COMPLETED ||
                    action == Intent.ACTION_TIME_CHANGED ||
                    action == Intent.ACTION_TIMEZONE_CHANGED) {

                    // AppDatabase.getInstance(context) can be used, but since RuntimeGraph handles it,
                    // we can get the db from there or directly if accessible.
                    // To keep it simple:
                    val db = io.github.magisk317.relay.android.data.db.AppDatabase.getInstance(context)
                    ScheduledTaskManager(context, db).scheduleAllActiveTasks()
                } else if (action == ScheduledTaskManager.ALARM_ACTION) {
                    val taskId = intent.getLongExtra(ScheduledTaskManager.EXTRA_TASK_ID, -1L)
                    if (taskId != -1L) {
                        ScheduledTaskExecutor.executeTask(context, taskId, "alarm")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
