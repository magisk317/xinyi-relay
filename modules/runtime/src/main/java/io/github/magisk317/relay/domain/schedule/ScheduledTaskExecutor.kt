package io.github.magisk317.relay.domain.schedule

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.ScheduledTask
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry
import io.github.magisk317.relay.runtime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import io.github.magisk317.xposed.logging.MagiskOtel

object ScheduledTaskExecutor {
    private const val DEDUPE_WINDOW_MS = 60_000L
    private const val EARLY_TRIGGER_GRACE_MS = 30_000L

    suspend fun executeTask(context: Context, taskId: Long, source: String) {
        withContext(Dispatchers.IO) {
            if (!BuildConfig.ENABLE_SMS_CHANNEL) {
                XLog.w("ScheduledTask $taskId skipped: SMS channel disabled in current distribution")
                MagiskOtel.event(
                    name = "sms.schedule",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "execute",
                        "source" to source,
                        "reason" to "sms_channel_disabled",
                    ),
                    statusOk = true,
                )
                return@withContext
            }

            if (!AppPreferencesDataStore.getBoolean(
                    context = context,
                    key = PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                    defaultValue = PrefConst.DEFAULT_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                )
            ) {
                XLog.i("ScheduledTask $taskId skipped: mobile entitlement unavailable")
                MagiskOtel.event(
                    name = "sms.schedule",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "execute",
                        "source" to source,
                        "reason" to "mobile_entitlement",
                    ),
                    statusOk = true,
                )
                return@withContext
            }

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
                MagiskOtel.event(
                    name = "sms.schedule",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "execute",
                        "source" to source,
                        "reason" to "not_claimed",
                    ),
                    statusOk = true,
                )
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
                    }.onSuccess {
                        MagiskOtel.event(
                            name = "sms.schedule",
                            attributes = mapOf(
                                "result" to "ok",
                                "duration_ms" to "0",
                                "process" to "app",
                                "stage" to "execute",
                                "source" to source,
                                "reason" to "sms_sent",
                            ),
                            statusOk = true,
                        )
                    }.onFailure {
                        XLog.e("ScheduledTask $taskId SMS failed", it)
                        MagiskOtel.event(
                            name = "sms.schedule",
                            attributes = mapOf(
                                "result" to "error",
                                "duration_ms" to "0",
                                "process" to "app",
                                "stage" to "execute",
                                "source" to source,
                                "reason" to it.javaClass.simpleName,
                            ),
                            statusOk = false,
                        )
                    }
                } else {
                    XLog.w("ScheduledTask $taskId skipped unsupported type=${task.taskType}")
                    MagiskOtel.event(
                        name = "sms.schedule",
                        attributes = mapOf(
                            "result" to "skip",
                            "duration_ms" to "0",
                            "process" to "app",
                            "stage" to "execute",
                            "source" to source,
                            "reason" to "unsupported_type",
                        ),
                        statusOk = true,
                    )
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
