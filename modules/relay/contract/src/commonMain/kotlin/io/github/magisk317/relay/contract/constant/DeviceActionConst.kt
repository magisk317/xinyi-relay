package io.github.magisk317.relay.contract.constant

/**
 * Wire vocabulary for one-shot device actions (see DeviceActionCommandResponse).
 *
 * Kept in the shared contract so the app, generated webui clients and sibling
 * projects (xsc / mipush) stay aligned on action names, params keys/values and
 * ack statuses without duplicating literals.
 */
object DeviceActionConst {
    const val SET_RINGER_MODE = "SET_RINGER_MODE"
    const val SET_INTERRUPTION_FILTER = "SET_INTERRUPTION_FILTER"

    const val RINGER_MODE_NORMAL = "normal"
    const val RINGER_MODE_SILENT = "silent"
    const val RINGER_MODE_VIBRATE = "vibrate"

    const val INTERRUPTION_FILTER_ALL = "all"
    const val INTERRUPTION_FILTER_PRIORITY = "priority"
    const val INTERRUPTION_FILTER_NONE = "none"
    const val INTERRUPTION_FILTER_ALARMS = "alarms"

    const val STATUS_DONE = "done"
    const val STATUS_FAILED = "failed"

    const val FAILURE_UNSUPPORTED_ACTION = "unsupported_action"
    const val FAILURE_INVALID_PARAMS = "invalid_params"
    const val FAILURE_NOTIFICATION_POLICY_ACCESS_REQUIRED = "notification_policy_access_required"
    const val FAILURE_AUDIO_SERVICE_UNAVAILABLE = "audio_service_unavailable"
    const val FAILURE_NOTIFICATION_SERVICE_UNAVAILABLE = "notification_service_unavailable"
    const val FAILURE_EXECUTION_ERROR = "execution_error"
}
