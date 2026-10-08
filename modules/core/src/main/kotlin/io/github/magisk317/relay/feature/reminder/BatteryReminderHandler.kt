package io.github.magisk317.relay.feature.reminder

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.event.RelayEvent
import io.github.magisk317.relay.domain.pipeline.EventPipeline
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * 电量提醒处理器。
 *
 * 内部状态位与用户设置统一保存在应用私有 DataStore 中。
 */
class BatteryReminderHandler(
    private val context: Context,
    private val eventPipeline: EventPipeline,
) {
    suspend fun handle(
        batteryIntent: Intent,
        scheduleNext: Boolean,
        reason: String,
    ) {
        val settings = RuntimeSettingsCache.getSpecialAlertSettings(
            RuntimeGraph.from(context).settingsRepository,
        )
        val lowEnabled = settings.lowBatteryReminderEnabled
        val fullEnabled = settings.fullBatteryReminderEnabled
        val chargingChangeEnabled = settings.chargingChangeReminderEnabled
        if (!lowEnabled && !fullEnabled && !chargingChangeEnabled) {
            if (scheduleNext) {
                LowBatteryReminderScheduler.cancel(context, reason = "disabled:$reason")
            }
            MagiskOtel.event(
                name = "battery.reminder",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "handle",
                    "reason" to "disabled",
                    "source" to reason,
                ),
                statusOk = true,
            )
            return
        }
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0 || scale <= 0) {
            if (scheduleNext) {
                LowBatteryReminderScheduler.scheduleNext(
                    context,
                    reason = "battery_invalid:$reason",
                    immediate = false,
                )
            }
            return
        }
        val percent = (level * 100f / scale).toInt()

        val threshold = if (lowEnabled) settings.lowBatteryThreshold else 0
        val wasBelow = if (lowEnabled) {
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_INTERNAL_LOW_BATTERY_BELOW, false)
        } else {
            false
        }
        val wasFull = if (fullEnabled) {
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_INTERNAL_FULL_BATTERY_ABOVE, false)
        } else {
            false
        }

        if (lowEnabled) {
            if (percent <= threshold) {
                if (!wasBelow) {
                    sendLowReminder(percent, threshold)
                    AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_INTERNAL_LOW_BATTERY_BELOW, true)
                }
            } else if (wasBelow) {
                AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_INTERNAL_LOW_BATTERY_BELOW, false)
            }
        }

        if (fullEnabled) {
            val isFull = percent >= 100 || status == BatteryManager.BATTERY_STATUS_FULL
            if (isFull) {
                if (!wasFull) {
                    sendFullReminder(percent)
                    AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_INTERNAL_FULL_BATTERY_ABOVE, true)
                }
            } else if (wasFull) {
                AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_INTERNAL_FULL_BATTERY_ABOVE, false)
            }
        }

        if (chargingChangeEnabled) {
            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val wasPlugged = AppPreferencesDataStore.getInt(context, PrefConst.KEY_INTERNAL_CHARGING_STATE, -1)
            if (wasPlugged >= 0 && plugged != wasPlugged) {
                sendChargingChangeReminder(percent, plugged != 0)
            }
            AppPreferencesDataStore.setInt(context, PrefConst.KEY_INTERNAL_CHARGING_STATE, plugged)
        }

        if (scheduleNext) {
            LowBatteryReminderScheduler.scheduleNext(context, reason = "cycle:$reason", immediate = false)
        }
    }

    private suspend fun sendLowReminder(percent: Int, threshold: Int) {
        val senderId = RuntimeSettingsCache.getSpecialAlertSettings(
            RuntimeGraph.from(context).settingsRepository,
        ).lowBatteryChannelId.trim().toLongOrNull()
        if (senderId == null) {
            XLog.w("LowBattery reminder skipped: sender not set")
            MagiskOtel.event(
                name = "battery.reminder",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "low",
                    "reason" to "sender_not_set",
                ),
                statusOk = true,
            )
            return
        }
        val title = context.getString(R.string.low_battery_notification_title)
        val content = context.getString(R.string.low_battery_notification_content, percent, threshold)
        eventPipeline.process(
            event = RelayEvent.batteryReminder(
                title = title,
                content = content,
                timestamp = System.currentTimeMillis(),
                packageName = context.packageName,
                senderId = senderId,
                sourceType = "battery_low",
            ),
            traceId = "low_battery_${System.currentTimeMillis()}",
        )
        emitBattery(stage = "low", result = "ok", reason = "sent")
        XLog.i("LowBattery reminder routed through EventPipeline: pct=%d threshold=%d", percent, threshold)
    }

    private suspend fun sendFullReminder(percent: Int) {
        val senderId = RuntimeSettingsCache.getSpecialAlertSettings(
            RuntimeGraph.from(context).settingsRepository,
        ).fullBatteryChannelId.trim().toLongOrNull()
        if (senderId == null) {
            XLog.w("FullBattery reminder skipped: sender not set")
            MagiskOtel.event(
                name = "battery.reminder",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "full",
                    "reason" to "sender_not_set",
                ),
                statusOk = true,
            )
            return
        }
        val title = context.getString(R.string.full_battery_notification_title)
        val content = context.getString(R.string.full_battery_notification_content, percent)
        eventPipeline.process(
            event = RelayEvent.batteryReminder(
                title = title,
                content = content,
                timestamp = System.currentTimeMillis(),
                packageName = context.packageName,
                senderId = senderId,
                sourceType = "battery_full",
            ),
            traceId = "full_battery_${System.currentTimeMillis()}",
        )
        emitBattery(stage = "full", result = "ok", reason = "sent")
        XLog.i("FullBattery reminder routed through EventPipeline: pct=%d", percent)
    }

    private suspend fun sendChargingChangeReminder(percent: Int, isPluggedIn: Boolean) {
        val senderId = RuntimeSettingsCache.getSpecialAlertSettings(
            RuntimeGraph.from(context).settingsRepository,
        ).chargingChangeChannelId.trim().toLongOrNull()
        if (senderId == null) {
            XLog.w("ChargingChange reminder skipped: sender not set")
            MagiskOtel.event(
                name = "battery.reminder",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "charging_change",
                    "reason" to "sender_not_set",
                ),
                statusOk = true,
            )
            return
        }
        val title = context.getString(R.string.charging_change_notification_title)
        val content = if (isPluggedIn) {
            context.getString(R.string.charging_change_notification_content_plugged, percent)
        } else {
            context.getString(R.string.charging_change_notification_content_unplugged, percent)
        }
        eventPipeline.process(
            event = RelayEvent.batteryReminder(
                title = title,
                content = content,
                timestamp = System.currentTimeMillis(),
                packageName = context.packageName,
                senderId = senderId,
                sourceType = "charging_change",
            ),
            traceId = "charging_change_${System.currentTimeMillis()}",
        )
        emitBattery(stage = "charging_change", result = "ok", reason = "sent")
        XLog.i("ChargingChange reminder routed through EventPipeline: pct=%d plugged=%s", percent, isPluggedIn)
    }


    private fun emitBattery(stage: String, result: String, reason: String? = null, statusOk: Boolean = true) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "app",
            "stage" to stage,
        )
        if (reason != null) attrs["reason"] = reason
        MagiskOtel.event(name = "battery.reminder", attributes = attrs, statusOk = statusOk)
    }

    companion object {
        private val reminderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun handleAsync(
            context: Context,
            batteryIntent: Intent,
            scheduleNext: Boolean,
            reason: String,
            onComplete: (() -> Unit)? = null,
        ) {
            val appContext = context.applicationContext ?: context
            reminderScope.launch {
                runCatching {
                    BatteryReminderHandler(
                        context = appContext,
                        eventPipeline = RuntimeGraph.from(appContext).eventPipeline,
                    ).handle(
                        batteryIntent = batteryIntent,
                        scheduleNext = scheduleNext,
                        reason = reason,
                    )
                }.onFailure {
                    XLog.e("Battery reminder handling failed", it)
                }
                onComplete?.invoke()
            }
        }
    }
}
