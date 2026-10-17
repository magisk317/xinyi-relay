package io.github.magisk317.relay.ui.home.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.constant.DeviceActionConst
import io.github.magisk317.relay.contract.remote.DeviceActionCommandResponse
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.domain.action.DeviceActionExecutor
import io.github.magisk317.relay.domain.action.DeviceActionOutcome
import io.github.magisk317.relay.domain.recovery.RootShell
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Ringer mode shown on the device control page. */
internal enum class RingerLabel {
    NORMAL,
    SILENT,
    VIBRATE,
    UNKNOWN,
}

/** Interruption filter shown on the device control page. */
internal enum class FilterLabel {
    ALL,
    PRIORITY,
    NONE,
    ALARMS,
    UNKNOWN,
}

/** One locally triggered device control action. */
internal enum class DeviceControlAction(
    val action: String,
    val paramKey: String,
    val paramValue: String,
    @StringRes val labelRes: Int,
) {
    NORMAL(
        action = DeviceActionConst.SET_RINGER_MODE,
        paramKey = PARAM_KEY_MODE,
        paramValue = DeviceActionConst.RINGER_MODE_NORMAL,
        labelRes = R.string.device_control_action_normal,
    ),
    SILENT(
        action = DeviceActionConst.SET_RINGER_MODE,
        paramKey = PARAM_KEY_MODE,
        paramValue = DeviceActionConst.RINGER_MODE_SILENT,
        labelRes = R.string.device_control_action_silent,
    ),
    VIBRATE(
        action = DeviceActionConst.SET_RINGER_MODE,
        paramKey = PARAM_KEY_MODE,
        paramValue = DeviceActionConst.RINGER_MODE_VIBRATE,
        labelRes = R.string.device_control_action_vibrate,
    ),
    PRIORITY(
        action = DeviceActionConst.SET_INTERRUPTION_FILTER,
        paramKey = PARAM_KEY_FILTER,
        paramValue = DeviceActionConst.INTERRUPTION_FILTER_PRIORITY,
        labelRes = R.string.device_control_action_priority,
    ),
    ALL(
        action = DeviceActionConst.SET_INTERRUPTION_FILTER,
        paramKey = PARAM_KEY_FILTER,
        paramValue = DeviceActionConst.INTERRUPTION_FILTER_ALL,
        labelRes = R.string.device_control_action_all,
    ),
}

private const val PARAM_KEY_MODE = "mode"
private const val PARAM_KEY_FILTER = "filter"
private const val UNREADABLE_STATE = -1

internal fun ringerLabelOf(mode: Int): RingerLabel {
    return when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> RingerLabel.NORMAL
        AudioManager.RINGER_MODE_SILENT -> RingerLabel.SILENT
        AudioManager.RINGER_MODE_VIBRATE -> RingerLabel.VIBRATE
        else -> RingerLabel.UNKNOWN
    }
}

internal fun filterLabelOf(filter: Int): FilterLabel {
    return when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> FilterLabel.ALL
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> FilterLabel.PRIORITY
        NotificationManager.INTERRUPTION_FILTER_NONE -> FilterLabel.NONE
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> FilterLabel.ALARMS
        else -> FilterLabel.UNKNOWN
    }
}

@StringRes
internal fun ringerLabelTextRes(label: RingerLabel): Int {
    return when (label) {
        RingerLabel.NORMAL -> R.string.device_control_ringer_normal
        RingerLabel.SILENT -> R.string.device_control_ringer_silent
        RingerLabel.VIBRATE -> R.string.device_control_ringer_vibrate
        RingerLabel.UNKNOWN -> R.string.device_control_state_unknown
    }
}

@StringRes
internal fun filterLabelTextRes(label: FilterLabel): Int {
    return when (label) {
        FilterLabel.ALL -> R.string.device_control_filter_all
        FilterLabel.PRIORITY -> R.string.device_control_filter_priority
        FilterLabel.NONE -> R.string.device_control_filter_none
        FilterLabel.ALARMS -> R.string.device_control_filter_alarms
        FilterLabel.UNKNOWN -> R.string.device_control_state_unknown
    }
}

@StringRes
internal fun failureReasonTextRes(reason: String): Int {
    return when (reason) {
        DeviceActionConst.FAILURE_UNSUPPORTED_ACTION ->
            R.string.device_control_failure_unsupported_action
        DeviceActionConst.FAILURE_INVALID_PARAMS ->
            R.string.device_control_failure_invalid_params
        DeviceActionConst.FAILURE_NOTIFICATION_POLICY_ACCESS_REQUIRED ->
            R.string.device_control_failure_notification_policy
        DeviceActionConst.FAILURE_AUDIO_SERVICE_UNAVAILABLE ->
            R.string.device_control_failure_audio_service
        DeviceActionConst.FAILURE_NOTIFICATION_SERVICE_UNAVAILABLE ->
            R.string.device_control_failure_notification_service
        else -> R.string.device_control_failure_execution
    }
}

private fun readRingerMode(context: Context): Int {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    return audioManager?.ringerMode ?: UNREADABLE_STATE
}

private fun readInterruptionFilter(context: Context): Int {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    return manager?.currentInterruptionFilter ?: UNREADABLE_STATE
}

private fun readPolicyAccess(context: Context): Boolean {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    return manager?.isNotificationPolicyAccessGranted ?: false
}

/**
 * Device control page: quick local ringer / interruption actions, the
 * current platform state, root availability and the last execution
 * outcome. Local actions run through the same [DeviceActionExecutor]
 * the remote agent command loop uses, without going through a server.
 */
@Composable
fun DeviceControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { AppSnackbarHostState() }
    val executor = remember(context) { DeviceActionExecutor(context) }
    var ringerLabel by remember { mutableStateOf(ringerLabelOf(readRingerMode(context))) }
    var filterLabel by remember { mutableStateOf(filterLabelOf(readInterruptionFilter(context))) }
    var policyAccessGranted by remember { mutableStateOf(readPolicyAccess(context)) }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var lastOutcome by remember { mutableStateOf<DeviceActionOutcome?>(null) }
    val successText = stringResource(id = R.string.device_control_result_success)
    val noneText = stringResource(id = R.string.device_control_result_none)

    LaunchedEffect(Unit) {
        rootAvailable = withContext(Dispatchers.IO) {
            runCatching { RootShell.canUseRoot() }.getOrDefault(false)
        }
    }

    fun refreshState() {
        ringerLabel = ringerLabelOf(readRingerMode(context))
        filterLabel = filterLabelOf(readInterruptionFilter(context))
        policyAccessGranted = readPolicyAccess(context)
    }

    fun execute(action: DeviceControlAction) {
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                executor.execute(
                    DeviceActionCommandResponse(
                        id = 0L,
                        action = action.action,
                        params = buildJsonObject { put(action.paramKey, action.paramValue) },
                    ),
                )
            }
            refreshState()
            lastOutcome = outcome
            val message = if (outcome.success) {
                successText
            } else {
                context.getString(failureReasonTextRes(outcome.failureReason))
            }
            snackbarHostState.showLatestSnackbar(message)
        }
    }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DeviceControlAction.entries.take(3).forEach { action ->
                    AppPrimaryButton(
                        onClick = { execute(action) },
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(id = action.labelRes))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DeviceControlAction.entries.drop(3).forEach { action ->
                    AppPrimaryButton(
                        onClick = { execute(action) },
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(id = action.labelRes))
                    }
                }
            }

            if (!policyAccessGranted) {
                AppPrimaryButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText(stringResource(id = R.string.device_control_policy_grant_action))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            DeviceControlStatusCard(
                title = stringResource(id = R.string.device_control_ringer_title),
                value = stringResource(id = ringerLabelTextRes(ringerLabel)),
            )
            DeviceControlStatusCard(
                title = stringResource(id = R.string.device_control_filter_title),
                value = stringResource(id = filterLabelTextRes(filterLabel)),
            )
            DeviceControlStatusCard(
                title = stringResource(id = R.string.device_control_policy_access_title),
                value = if (policyAccessGranted) {
                    stringResource(id = R.string.device_control_policy_granted)
                } else {
                    stringResource(id = R.string.device_control_policy_missing)
                },
            )
            DeviceControlStatusCard(
                title = stringResource(id = R.string.device_control_root_title),
                value = when (rootAvailable) {
                    null -> stringResource(id = R.string.device_control_root_probing)
                    true -> stringResource(id = R.string.device_control_root_available)
                    false -> stringResource(id = R.string.device_control_root_unavailable)
                },
            )
            DeviceControlStatusCard(
                title = stringResource(id = R.string.device_control_last_result_title),
                value = when (val outcome = lastOutcome) {
                    null -> noneText
                    else -> if (outcome.success) {
                        successText
                    } else {
                        stringResource(
                            id = failureReasonTextRes(
                                outcome.failureReason.ifBlank { DeviceActionConst.FAILURE_EXECUTION_ERROR },
                            ),
                        )
                    }
                },
            )
        }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> DeviceControlScreenMiuix(
            title = stringResource(id = R.string.device_control_title),
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = body,
        )

        UiKitStyle.Expressive -> DeviceControlScreenMaterial(
            title = stringResource(id = R.string.device_control_title),
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = body,
        )
    }
}

@Composable
private fun DeviceControlStatusCard(title: String, value: String) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = appColor(AppColorRole.SurfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppText(text = title, role = AppTextRole.Subtitle)
            AppText(text = value, role = AppTextRole.Body)
        }
    }
}
