package io.github.magisk317.relay.ui.scheduled

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.ScheduledTask
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import kotlinx.coroutines.launch

internal fun ScheduledTask.permissionWarningMessage(context: Context): String? {
    if (status != ScheduledTask.STATUS_ENABLED || taskType != ScheduledTask.TASK_TYPE_SMS) {
        return null
    }

    val missing = buildList {
        if (!context.hasPermission(Manifest.permission.SEND_SMS)) {
            add(context.getString(R.string.scheduled_task_permission_send_sms))
        }
        if (simSlot > 0 && !context.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            add(context.getString(R.string.scheduled_task_permission_read_phone_state))
        }
        if (!context.canScheduleExactAlarmsCompat()) {
            add(context.getString(R.string.scheduled_task_permission_exact_alarm))
        }
    }

    if (missing.isEmpty()) return null
    return context.getString(
        R.string.scheduled_task_permission_warning,
        missing.joinToString(context.getString(R.string.scheduled_task_permission_separator)),
    )
}

@Composable
internal fun rememberScheduledTaskPermissionPrompter(): (ScheduledTask, () -> Unit) -> Unit {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    var pendingRequest by remember { mutableStateOf<ScheduledTaskPermissionRequest?>(null) }

    fun showWarningIfStillMissing(task: ScheduledTask) {
        task.permissionWarningMessage(context)?.let { warning ->
            coroutineScope.launch { snackbarHostState.showSnackbar(warning) }
        }
    }

    fun finishRequest(request: ScheduledTaskPermissionRequest) {
        showWarningIfStillMissing(request.task)
        request.onComplete()
    }

    fun launchExactAlarmSettings(request: ScheduledTaskPermissionRequest): Boolean {
        pendingRequest = request
        return runCatching {
            request.exactAlarmLauncher.launch(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
            )
        }.isSuccess
    }

    val exactAlarmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        pendingRequest?.let { request ->
            pendingRequest = null
            finishRequest(request)
        }
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        pendingRequest?.let { request ->
            if (request.task.needsExactAlarmPermission(context)) {
                if (!launchExactAlarmSettings(request)) {
                    pendingRequest = null
                    finishRequest(request)
                }
            } else {
                pendingRequest = null
                finishRequest(request)
            }
        }
    }

    return remember(context, runtimePermissionLauncher, exactAlarmLauncher) {
        { task, onComplete ->
            val request = ScheduledTaskPermissionRequest(
                task = task,
                onComplete = onComplete,
                exactAlarmLauncher = exactAlarmLauncher,
            )
            val runtimePermissions = task.missingRuntimePermissions(context)
            when {
                runtimePermissions.isNotEmpty() -> {
                    pendingRequest = request
                    runtimePermissionLauncher.launch(runtimePermissions.toTypedArray())
                }

                task.needsExactAlarmPermission(context) -> {
                    if (!launchExactAlarmSettings(request)) {
                        pendingRequest = null
                        finishRequest(request)
                    }
                }

                else -> onComplete()
            }
        }
    }
}

private data class ScheduledTaskPermissionRequest(
    val task: ScheduledTask,
    val onComplete: () -> Unit,
    val exactAlarmLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
)

private fun ScheduledTask.missingRuntimePermissions(context: Context): List<String> {
    if (status != ScheduledTask.STATUS_ENABLED || taskType != ScheduledTask.TASK_TYPE_SMS) {
        return emptyList()
    }
    return buildList {
        if (!context.hasPermission(Manifest.permission.SEND_SMS)) {
            add(Manifest.permission.SEND_SMS)
        }
        if (simSlot > 0 && !context.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            add(Manifest.permission.READ_PHONE_STATE)
        }
    }
}

private fun ScheduledTask.needsExactAlarmPermission(context: Context): Boolean {
    if (status != ScheduledTask.STATUS_ENABLED) return false
    return !context.canScheduleExactAlarmsCompat()
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.canScheduleExactAlarmsCompat(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return true
    return alarmManager.canScheduleExactAlarms()
}
