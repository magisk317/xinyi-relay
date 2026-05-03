package io.github.magisk317.relay.domain.schedule

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.ScheduledTask
import io.github.magisk317.relay.sender.SmsUtils
import io.github.magisk317.relay.sender.config.SmsSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ScheduledTaskExecutor {
    private const val DEDUPE_WINDOW_MS = 60_000L

    suspend fun executeTask(context: Context, taskId: Long, source: String) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val dao = db.scheduledTaskDao()

            val now = System.currentTimeMillis()
            val claimed = dao.markRunIfDue(
                id = taskId,
                runTime = now,
                dedupeBefore = now - DEDUPE_WINDOW_MS,
            )
            if (claimed == 0) {
                XLog.i("Task $taskId skipped, disabled or already claimed recently")
                return@withContext
            }

            val task = dao.getById(taskId) ?: return@withContext
            XLog.i("Executing ScheduledTask $taskId from $source")

            if (task.taskType == ScheduledTask.TASK_TYPE_SMS) {
                val setting = SmsSetting(
                    simSlot = task.simSlot,
                    mobiles = task.mobiles,
                    onlyNoNetwork = false
                )
                val msgInfo = MsgInfo(
                    content = task.content,
                    from = "ScheduledTask",
                    date = java.util.Date(),
                    simInfo = ""
                )

                try {
                    SmsUtils.sendMsg(context, setting, msgInfo, waitForSentResult = true)
                    XLog.i("ScheduledTask $taskId sent SMS successfully")
                } catch (e: Exception) {
                    XLog.e("ScheduledTask $taskId SMS failed", e)
                }
            }

            ScheduledTaskManager(context, db).rescheduleTask(task.id)
        }
    }
}
