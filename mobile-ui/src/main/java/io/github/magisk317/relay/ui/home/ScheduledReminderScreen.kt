package io.github.magisk317.relay.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.contract.settings.SpecialAlertSettingsSnapshot
import io.github.magisk317.relay.contract.settings.SpecialAlertSettingsUpdate
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler
import io.github.magisk317.relay.ui.sender.SenderViewModel
import io.github.magisk317.relay.ui.sender.displayName
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private data class ChannelOption(
    val id: String,
    val label: String,
)

private fun countKeywords(raw: String): Int {
    return raw
        .split('\n', '\r', ',', '，', ';', '；')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .size
}

@Composable
fun SectionCard(
    title: String,
    accordionMode: Boolean,
    sectionExpanded: Boolean,
    onExpandedChange: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Const.PADDING_SMALL.dp),
    ) {
        io.github.magisk317.uikit.preference.SectionCard(
            title = title,
            accordionMode = accordionMode,
            sectionExpanded = sectionExpanded,
            onExpandedChange = onExpandedChange,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDropdown(
    title: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<ChannelOption>,
    onSelect: (ChannelOption) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Const.PADDING_MEDIUM.dp,
            vertical = 6.dp,
        ),
    )
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Const.PADDING_MEDIUM.dp),
    ) {
        TextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledReminderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val notifySaved = {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }
    var settings by remember { mutableStateOf<SpecialAlertSettingsSnapshot?>(null) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showSmsKeywordDialog by remember { mutableStateOf(false) }
    var showAppKeywordDialog by remember { mutableStateOf(false) }
    var lowExpanded by remember { mutableStateOf(false) }
    var fullExpanded by remember { mutableStateOf(false) }
    var callExpanded by remember { mutableStateOf(false) }
    var expandBatterySection by remember { mutableStateOf(true) }
    var expandCallSection by remember { mutableStateOf(true) }
    var expandKeywordSection by remember { mutableStateOf(true) }
    var channelOptions by remember { mutableStateOf<List<ChannelOption>>(emptyList()) }

    val isGithubFlavor = BuildConfig.FLAVOR == "github"
    val callPermissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        callPermissionGranted.value = granted
        if (granted) {
            context.sendBroadcast(Intent(PrefConst.ACTION_CALL_ALERT_MONITOR_REFRESH).setPackage(context.packageName))
        }
    }

    val senderViewModel: SenderViewModel = koinViewModel()
    val senderList by senderViewModel.senderList.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        settings = repository.getSpecialAlertSettings()
    }

    LaunchedEffect(senderList, settings) {
        val current = settings ?: return@LaunchedEffect
        val options = senderList
            .filter { it.status == 1 }
            .map { sender ->
                val label = sender.displayName(context)
                ChannelOption(sender.id.toString(), label)
            }
        channelOptions = options
        if (options.isEmpty()) return@LaunchedEffect

        val ids = options.map { it.id }.toSet()
        var lowChannelId = current.lowBatteryChannelId
        var fullChannelId = current.fullBatteryChannelId
        var callChannelId = current.callAlertChannelId
        var changed = false
        if (lowChannelId.isBlank() || lowChannelId !in ids) {
            lowChannelId = options.first().id
            changed = true
        }
        if (fullChannelId.isBlank() || fullChannelId !in ids) {
            fullChannelId = options.first().id
            changed = true
        }
        if (callChannelId.isBlank() || callChannelId !in ids) {
            callChannelId = options.first().id
            changed = true
        }
        if (changed) {
            settings = repository.updateSpecialAlertSettings(
                SpecialAlertSettingsUpdate(
                    lowBatteryChannelId = lowChannelId,
                    fullBatteryChannelId = fullChannelId,
                    callAlertChannelId = callChannelId,
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.scheduled_reminder_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = {
            io.github.magisk317.relay.ui.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        val lowChannelLabel = channelOptions.firstOrNull { it.id == current.lowBatteryChannelId }?.label
            ?: current.lowBatteryChannelId
        val fullChannelLabel = channelOptions.firstOrNull { it.id == current.fullBatteryChannelId }?.label
            ?: current.fullBatteryChannelId
        val callChannelLabel = channelOptions.firstOrNull { it.id == current.callAlertChannelId }?.label
            ?: current.callAlertChannelId

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Const.PADDING_SMALL.dp, vertical = Const.PADDING_MEDIUM.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(
                title = stringResource(id = R.string.scheduled_reminder_battery_section_title),
                accordionMode = true,
                sectionExpanded = expandBatterySection,
                onExpandedChange = { expandBatterySection = !expandBatterySection },
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.scheduled_reminder_low_battery_title),
                    summary = stringResource(id = R.string.scheduled_reminder_low_battery_summary),
                    checked = current.lowBatteryReminderEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateSpecialAlertSettings(
                            SpecialAlertSettingsUpdate(lowBatteryReminderEnabled = enabled),
                        )
                        if (enabled) {
                            LowBatteryReminderScheduler.scheduleNext(context, reason = "ui_toggle", immediate = true)
                        } else {
                            LowBatteryReminderScheduler.syncFromPrefs(context, reason = "ui_toggle")
                            repository.clearBatteryReminderRuntimeFlags(clearLowBatteryBelow = true)
                        }
                        notifySaved()
                    }
                }
                if (current.lowBatteryReminderEnabled) {
                    Item(
                        title = stringResource(id = R.string.scheduled_reminder_threshold_title),
                        summary = stringResource(
                            id = R.string.scheduled_reminder_threshold_summary,
                            current.lowBatteryThreshold,
                        ),
                    ) { showThresholdDialog = true }
                    ChannelDropdown(
                        title = stringResource(id = R.string.scheduled_reminder_channel_title),
                        value = lowChannelLabel,
                        expanded = lowExpanded,
                        onExpandedChange = { lowExpanded = it },
                        options = channelOptions,
                    ) { option ->
                        lowExpanded = false
                        scope.launch {
                            settings = repository.updateSpecialAlertSettings(
                                SpecialAlertSettingsUpdate(lowBatteryChannelId = option.id),
                            )
                        }
                    }
                }

                StateSwitchItem(
                    title = stringResource(id = R.string.scheduled_reminder_full_battery_title),
                    summary = stringResource(id = R.string.scheduled_reminder_full_battery_summary),
                    checked = current.fullBatteryReminderEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateSpecialAlertSettings(
                            SpecialAlertSettingsUpdate(fullBatteryReminderEnabled = enabled),
                        )
                        if (enabled) {
                            LowBatteryReminderScheduler.scheduleNext(context, reason = "ui_toggle_full", immediate = true)
                        } else {
                            LowBatteryReminderScheduler.syncFromPrefs(context, reason = "ui_toggle_full")
                            repository.clearBatteryReminderRuntimeFlags(clearFullBatteryAbove = true)
                        }
                        notifySaved()
                    }
                }
                if (current.fullBatteryReminderEnabled) {
                    ChannelDropdown(
                        title = stringResource(id = R.string.scheduled_reminder_channel_title),
                        value = fullChannelLabel,
                        expanded = fullExpanded,
                        onExpandedChange = { fullExpanded = it },
                        options = channelOptions,
                    ) { option ->
                        fullExpanded = false
                        scope.launch {
                            settings = repository.updateSpecialAlertSettings(
                                SpecialAlertSettingsUpdate(fullBatteryChannelId = option.id),
                            )
                        }
                    }
                }
            }

            SectionCard(
                title = stringResource(id = R.string.call_alert_section_title),
                accordionMode = true,
                sectionExpanded = expandCallSection,
                onExpandedChange = { expandCallSection = !expandCallSection },
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.call_alert_forward_title))
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.call_alert_forward_summary_from_relay),
                        )
                    },
                )
                StateSwitchItem(
                    title = stringResource(id = R.string.call_alert_local_title),
                    summary = stringResource(id = R.string.call_alert_local_summary),
                    checked = current.callAlertLocalEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateSpecialAlertSettings(
                            SpecialAlertSettingsUpdate(callAlertLocalEnabled = enabled),
                        )
                        context.sendBroadcast(Intent(PrefConst.ACTION_CALL_ALERT_MONITOR_REFRESH).setPackage(context.packageName))
                        notifySaved()
                    }
                }
                if (current.callAlertLocalEnabled) {
                    ChannelDropdown(
                        title = stringResource(id = R.string.scheduled_reminder_channel_title),
                        value = callChannelLabel,
                        expanded = callExpanded,
                        onExpandedChange = { callExpanded = it },
                        options = channelOptions,
                    ) { option ->
                        callExpanded = false
                        scope.launch {
                            settings = repository.updateSpecialAlertSettings(
                                SpecialAlertSettingsUpdate(callAlertChannelId = option.id),
                            )
                        }
                    }
                }
                if (isGithubFlavor) {
                    Item(
                        title = stringResource(id = R.string.call_alert_permission_title),
                        summary = stringResource(
                            id = if (callPermissionGranted.value) {
                                R.string.call_alert_permission_granted
                            } else {
                                R.string.call_alert_permission_missing
                            },
                        ),
                    ) {
                        permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    }
                }
            }

            SectionCard(
                title = stringResource(id = R.string.special_alert_keyword_section_title),
                accordionMode = true,
                sectionExpanded = expandKeywordSection,
                onExpandedChange = { expandKeywordSection = !expandKeywordSection },
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.special_alert_sms_title),
                    summary = stringResource(id = R.string.special_alert_sms_summary),
                    checked = current.smsKeywordEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateSpecialAlertSettings(
                            SpecialAlertSettingsUpdate(smsKeywordEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
                if (current.smsKeywordEnabled) {
                    Item(
                        title = stringResource(id = R.string.special_alert_keywords_title),
                        summary = stringResource(
                            id = R.string.special_alert_keyword_summary,
                            countKeywords(current.smsKeywordKeywords),
                        ),
                    ) { showSmsKeywordDialog = true }
                    StateSwitchItem(
                        title = stringResource(id = R.string.special_alert_local_notification_title),
                        summary = stringResource(id = R.string.special_alert_local_notification_summary),
                        checked = current.smsKeywordNotificationEnabled,
                    ) { enabled ->
                        scope.launch {
                            settings = repository.updateSpecialAlertSettings(
                                SpecialAlertSettingsUpdate(smsKeywordNotificationEnabled = enabled),
                            )
                            notifySaved()
                        }
                    }
                    StateSwitchItem(
                        title = stringResource(id = R.string.special_alert_sound_title),
                        summary = stringResource(id = R.string.special_alert_sound_summary),
                        checked = current.smsKeywordSoundEnabled,
                    ) { enabled ->
                        scope.launch {
                            settings = repository.updateSpecialAlertSettings(
                                SpecialAlertSettingsUpdate(smsKeywordSoundEnabled = enabled),
                            )
                            notifySaved()
                        }
                    }
                    StateSwitchItem(
                        title = stringResource(id = R.string.special_alert_vibrate_title),
                        summary = stringResource(id = R.string.special_alert_vibrate_summary),
                        checked = current.smsKeywordVibrateEnabled,
                    ) { enabled ->
                        scope.launch {
                            settings = repository.updateSpecialAlertSettings(
                                SpecialAlertSettingsUpdate(smsKeywordVibrateEnabled = enabled),
                            )
                            notifySaved()
                        }
                    }
                }

                StateSwitchItem(
                    title = stringResource(id = R.string.special_alert_app_title),
                    summary = stringResource(id = R.string.special_alert_app_summary),
                    checked = current.appKeywordEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateSpecialAlertSettings(
                            SpecialAlertSettingsUpdate(appKeywordEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
                if (current.appKeywordEnabled) {
                    Item(
                        title = stringResource(id = R.string.special_alert_keywords_title),
                        summary = stringResource(
                            id = R.string.special_alert_keyword_summary,
                            countKeywords(current.appKeywordKeywords),
                        ),
                    ) { showAppKeywordDialog = true }
                    StateSwitchItem(
                        title = stringResource(id = R.string.special_alert_local_notification_title),
                        summary = stringResource(id = R.string.special_alert_local_notification_summary),
                        checked = current.appKeywordNotificationEnabled,
                    ) { enabled ->
                        scope.launch {
                            settings = repository.updateSpecialAlertSettings(
                                SpecialAlertSettingsUpdate(appKeywordNotificationEnabled = enabled),
                            )
                            notifySaved()
                        }
                    }
                    StateSwitchItem(
                        title = stringResource(id = R.string.special_alert_sound_title),
                        summary = stringResource(id = R.string.special_alert_sound_summary),
                        checked = current.appKeywordSoundEnabled,
                    ) { enabled ->
                        scope.launch {
                            settings = repository.updateSpecialAlertSettings(
                                SpecialAlertSettingsUpdate(appKeywordSoundEnabled = enabled),
                            )
                            notifySaved()
                        }
                    }
                    StateSwitchItem(
                        title = stringResource(id = R.string.special_alert_vibrate_title),
                        summary = stringResource(id = R.string.special_alert_vibrate_summary),
                        checked = current.appKeywordVibrateEnabled,
                    ) { enabled ->
                        scope.launch {
                            settings = repository.updateSpecialAlertSettings(
                                SpecialAlertSettingsUpdate(appKeywordVibrateEnabled = enabled),
                            )
                            notifySaved()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
        }
    }

    val current = settings
    if (showThresholdDialog && current != null) {
        val thresholdInvalid = stringResource(id = R.string.scheduled_reminder_threshold_invalid)
        TextInputDialog(
            title = stringResource(id = R.string.scheduled_reminder_threshold_title),
            initialValue = current.lowBatteryThreshold.toString(),
            onDismiss = { showThresholdDialog = false },
            supportingText = stringResource(id = R.string.scheduled_reminder_threshold_hint),
            validator = { value ->
                val parsed = value.trim().toIntOrNull()
                if (parsed == null || parsed !in 1..100) {
                    thresholdInvalid
                } else {
                    null
                }
            },
        ) { value ->
            val bounded = value.trim().toIntOrNull()?.coerceIn(1, 100) ?: PrefConst.LOW_BATTERY_THRESHOLD_DEFAULT
            showThresholdDialog = false
            scope.launch {
                settings = repository.updateSpecialAlertSettings(
                    SpecialAlertSettingsUpdate(lowBatteryThreshold = bounded),
                )
                if (current.lowBatteryReminderEnabled) {
                    LowBatteryReminderScheduler.scheduleNext(context, reason = "threshold_update", immediate = true)
                }
            }
        }
    }

    if (showSmsKeywordDialog && current != null) {
        TextInputDialog(
            title = stringResource(id = R.string.special_alert_sms_keywords_dialog_title),
            initialValue = current.smsKeywordKeywords,
            onDismiss = { showSmsKeywordDialog = false },
            singleLine = false,
            maxLines = 8,
            supportingText = stringResource(id = R.string.special_alert_keywords_hint),
        ) { value ->
            showSmsKeywordDialog = false
            scope.launch {
                settings = repository.updateSpecialAlertSettings(
                    SpecialAlertSettingsUpdate(smsKeywordKeywords = value),
                )
            }
        }
    }

    if (showAppKeywordDialog && current != null) {
        TextInputDialog(
            title = stringResource(id = R.string.special_alert_app_keywords_dialog_title),
            initialValue = current.appKeywordKeywords,
            onDismiss = { showAppKeywordDialog = false },
            singleLine = false,
            maxLines = 8,
            supportingText = stringResource(id = R.string.special_alert_keywords_hint),
        ) { value ->
            showAppKeywordDialog = false
            scope.launch {
                settings = repository.updateSpecialAlertSettings(
                    SpecialAlertSettingsUpdate(appKeywordKeywords = value),
                )
            }
        }
    }
}
