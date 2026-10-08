package io.github.magisk317.relay.receiver

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.feature.reminder.BatteryReminderHandler
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object LowBatteryReminderAlarmHandler {
    private const val NANOS_PER_MILLI = 1_000_000L

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)

    const val action: String = PrefConst.ACTION_LOW_BATTERY_REMINDER
    private val alarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handleAsync(context: Context, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        alarmScope.launch {
            val startedAt = System.nanoTime()
            runCatching {
                val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (batteryIntent == null) {
                    LowBatteryReminderScheduler.scheduleNext(
                        appContext,
                        reason = "battery_missing",
                        immediate = false,
                    )
                    MagiskOtel.event(
                        name = "battery.reminder",
                        attributes = mapOf(
                            "result" to "skip",
                            "duration_ms" to elapsedMs(startedAt).toString(),
                            "process" to "main",
                            "stage" to "alarm",
                            "reason" to "battery_missing",
                        ),
                        statusOk = true,
                    )
                    return@runCatching
                }
                BatteryReminderHandler(
                    context = appContext,
                    eventPipeline = RuntimeGraph.from(appContext).eventPipeline,
                ).handle(
                    batteryIntent = batteryIntent,
                    scheduleNext = true,
                    reason = "alarm_cycle",
                )
                MagiskOtel.event(
                    name = "battery.reminder",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to elapsedMs(startedAt).toString(),
                        "process" to "main",
                        "stage" to "alarm",
                        "reason" to "alarm_cycle",
                    ),
                    statusOk = true,
                )
            }.onFailure {
                XLog.e("LowBatteryReminder alarm handling failed", it)
                MagiskOtel.event(
                    name = "battery.reminder",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to elapsedMs(startedAt).toString(),
                        "process" to "main",
                        "stage" to "alarm",
                        "reason" to "alarm_failed",
                        "error_class" to it.javaClass.simpleName,
                    ),
                    statusOk = false,
                )
            }
            onComplete?.invoke()
        }
    }
}
