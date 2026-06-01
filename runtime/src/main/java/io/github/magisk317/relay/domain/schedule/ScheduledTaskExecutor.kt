package io.github.magisk317.relay.domain.schedule

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.ScheduledTask
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

object ScheduledTaskExecutor {
    private const val DEDUPE_WINDOW_MS = 60_000L
    private const val EARLY_TRIGGER_GRACE_MS = 30_000L

    suspend fun executeTask(context: Context, taskId: Long, source: String) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val dao = db.scheduledTaskDao()

            val now = System.currentTimeMillis()
            val claimed = dao.claimRunIfDue(
                id = taskId,
                dueBefore = now + EARLY_TRIGGER_GRACE_MS,
                dedupeBefore = now - DEDUPE_WINDOW_MS,
            )
            if (claimed == 0) {
                XLog.i("Task $taskId skipped, disabled, stale, early, or already claimed recently")
                return@withContext
            }

            val task = dao.getById(taskId) ?: return@withContext
            XLog.i("Executing ScheduledTask $taskId from $source")

            try {
                if (task.taskType == ScheduledTask.TASK_TYPE_SMS) {
                    val msgInfo = MsgInfo(
                        content = task.content,
                        from = "ScheduledTask",
                        date = java.util.Date(),
                        simInfo = "",
                        simSlot = task.simSlot,
                    )

                    sendScheduledSms {
                        SenderRuntimeServiceRegistry.requireInstalled().scheduledSmsSender.sendSms(
                            context = context,
                            simSlot = task.simSlot,
                            mobiles = task.mobiles,
                            msgInfo = msgInfo,
                            waitForSentResult = true,
                        )
                        dao.markRunSucceeded(taskId, System.currentTimeMillis())
                        XLog.i("ScheduledTask $taskId sent SMS successfully")
                    }.onFailure {
                        XLog.e("ScheduledTask $taskId SMS failed", it)
                    }
                } else {
                    XLog.w("ScheduledTask $taskId skipped unsupported type=${task.taskType}")
                }
            } finally {
                ScheduledTaskManager(context, db).rescheduleTask(task.id)
            }
        }
    }

    private suspend inline fun sendScheduledSms(block: suspend () -> Unit): Result<Unit> {
        return try {
            block()
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            Result.failure(e)
        }
    }
}
