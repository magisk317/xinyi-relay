package io.github.magisk317.relay.domain.schedule

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.SmsUtils
import io.github.magisk317.relay.sender.config.SmsSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ScheduledTaskExecutor {
    suspend fun executeTask(context: Context, taskId: Long, source: String) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val task = db.scheduledTaskDao().getById(taskId) ?: return@withContext

            val now = System.currentTimeMillis()
            // Deduplication logic: If last run was less than 1 minute ago, skip
            if (now - task.lastRunTime < 60_000L) {
                XLog.i("Task \${taskId} skipped (dedup), already ran recently.")
                return@withContext
            }

            XLog.i("Executing ScheduledTask \${taskId} from \$source")

            if (task.taskType == "sms") {
                val setting = SmsSetting(
                    simSlot = task.simSlot,
                    mobiles = task.mobiles,
                    onlyNoNetwork = false
                )
                val msgInfo = MsgInfo(
                    content = task.content,
                    from = "ScheduledTask"
                )

                try {
                    // This calls our updated SmsUtils (or current one if it handles it)
                    SmsUtils.sendMsg(context, setting, msgInfo)
                    XLog.i("ScheduledTask \${taskId} sent SMS successfully")
                } catch (e: Exception) {
                    XLog.e("ScheduledTask \${taskId} SMS failed", e)
                }
            }

            // Update last run time and reschedule
            task.lastRunTime = now
            db.scheduledTaskDao().update(task)

            ScheduledTaskManager(context, db).scheduleAllActiveTasks()
        }
    }
}
