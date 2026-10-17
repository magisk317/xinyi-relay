package io.github.magisk317.relay.domain.action

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import io.github.magisk317.relay.contract.constant.DeviceActionConst
import io.github.magisk317.relay.contract.remote.DeviceActionCommandResponse
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Outcome of executing one whitelisted device action locally. */
data class DeviceActionOutcome(
    val success: Boolean,
    val failureReason: String = "",
)

/**
 * Executes whitelisted one-shot device actions (ringer mode, interruption filter).
 *
 * The whitelist and params vocabulary mirror the backend (DeviceActionConst), so
 * an action that reached the device is already server-validated; validation is
 * repeated here because a rooted client can craft arbitrary pulls.
 *
 * First version runs the public platform APIs only. Root fallback command
 * strings need real-device verification per ROM and are intentionally not
 * faked; the dispatch seam below is where they land later.
 */
class DeviceActionExecutor(private val appContext: Context) {

    fun execute(action: DeviceActionCommandResponse): DeviceActionOutcome {
        val startedAt = System.nanoTime()
        val outcome = runCatching { dispatch(action) }
            .getOrElse { DeviceActionOutcome(success = false, failureReason = DeviceActionConst.FAILURE_EXECUTION_ERROR) }
        emit(action, outcome, startedAt)
        return outcome
    }

    private fun dispatch(action: DeviceActionCommandResponse): DeviceActionOutcome {
        return when (action.action) {
            DeviceActionConst.SET_RINGER_MODE -> applyRingerMode(action.params)
            DeviceActionConst.SET_INTERRUPTION_FILTER -> applyInterruptionFilter(action.params)
            else -> DeviceActionOutcome(success = false, failureReason = DeviceActionConst.FAILURE_UNSUPPORTED_ACTION)
        }
    }

    private fun applyRingerMode(params: JsonObject): DeviceActionOutcome {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return DeviceActionOutcome(success = false, failureReason = DeviceActionConst.FAILURE_AUDIO_SERVICE_UNAVAILABLE)
        val mode = params[PARAM_MODE]?.jsonPrimitive?.contentOrNull
        val ringerMode = when (mode) {
            DeviceActionConst.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_NORMAL
            DeviceActionConst.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_SILENT
            DeviceActionConst.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            else -> return DeviceActionOutcome(success = false, failureReason = DeviceActionConst.FAILURE_INVALID_PARAMS)
        }
        audioManager.ringerMode = ringerMode
        return DeviceActionOutcome(success = true)
    }

    private fun applyInterruptionFilter(params: JsonObject): DeviceActionOutcome {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return DeviceActionOutcome(
                success = false,
                failureReason = DeviceActionConst.FAILURE_NOTIFICATION_SERVICE_UNAVAILABLE,
            )
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return DeviceActionOutcome(
                success = false,
                failureReason = DeviceActionConst.FAILURE_NOTIFICATION_POLICY_ACCESS_REQUIRED,
            )
        }
        val filter = params[PARAM_FILTER]?.jsonPrimitive?.contentOrNull
        val interruptionFilter = when (filter) {
            DeviceActionConst.INTERRUPTION_FILTER_ALL -> NotificationManager.INTERRUPTION_FILTER_ALL
            DeviceActionConst.INTERRUPTION_FILTER_PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            DeviceActionConst.INTERRUPTION_FILTER_NONE -> NotificationManager.INTERRUPTION_FILTER_NONE
            DeviceActionConst.INTERRUPTION_FILTER_ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            else -> return DeviceActionOutcome(success = false, failureReason = DeviceActionConst.FAILURE_INVALID_PARAMS)
        }
        notificationManager.setInterruptionFilter(interruptionFilter)
        return DeviceActionOutcome(success = true)
    }

    private fun emit(action: DeviceActionCommandResponse, outcome: DeviceActionOutcome, startedAt: Long) {
        val attrs = mutableMapOf(
            "result" to if (outcome.success) "ok" else "error",
            "duration_ms" to ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L).toString(),
            "process" to "app",
            "stage" to "agent_action",
            "reason" to outcome.failureReason.ifBlank { "executed" },
            "action" to action.action,
        )
        MagiskOtel.event(name = "app.monitor", attributes = attrs, statusOk = outcome.success)
    }

    private companion object {
        const val PARAM_MODE = "mode"
        const val PARAM_FILTER = "filter"
    }
}
