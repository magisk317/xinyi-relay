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
import io.github.magisk317.xposed.logging.MagiskOtel

class ScheduledTaskReceiver : BroadcastReceiver() {
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        XLog.i("ScheduledTaskReceiver onReceive: $action")
        val startedAt = System.nanoTime()

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
                            emitSchedule(startedAt, result = "ok", reason = "reschedule", action = action)
                        }

                        ScheduledTaskManager.ALARM_ACTION -> {
                            val taskId = intent.getLongExtra(ScheduledTaskManager.EXTRA_TASK_ID, -1L)
                            if (taskId != -1L) {
                                ScheduledTaskExecutor.executeTask(appContext, taskId, "alarm")
                                emitSchedule(startedAt, result = "ok", reason = "alarm_execute", action = action)
                            } else {
                                emitSchedule(startedAt, result = "skip", reason = "missing_task_id", action = action)
                            }
                        }
                        else -> emitSchedule(startedAt, result = "skip", reason = "unhandled_action", action = action)
                    }
                }.onFailure { e ->
                    XLog.e("ScheduledTaskReceiver failed action=$action", e)
                    emitSchedule(
                        startedAt,
                        result = "error",
                        statusOk = false,
                        reason = e.javaClass.simpleName,
                        action = action,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun emitSchedule(
        startedAt: Long,
        result: String,
        statusOk: Boolean = true,
        reason: String? = null,
        action: String? = null,
    ) {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "main",
        )
        if (reason != null) attrs["reason"] = reason
        if (!action.isNullOrBlank()) attrs["action"] = action
        MagiskOtel.event(name = "sms.schedule", attributes = attrs, statusOk = statusOk)
    }
}
