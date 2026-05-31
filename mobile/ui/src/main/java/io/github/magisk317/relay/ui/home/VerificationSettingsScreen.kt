@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.magisk317.relay.android.common.utils.NotificationUtils
import io.github.magisk317.relay.contract.constant.CodeNotificationOwner
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.RecordSettingsSnapshot
import io.github.magisk317.relay.contract.settings.VerificationSettingsSnapshot
import io.github.magisk317.relay.contract.settings.VerificationSettingsUpdate
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.ui.common.filterNonNegativeIntegerInput
import io.github.magisk317.relay.ui.common.normalizeIntegerInput
import io.github.magisk317.relay.ui.common.parseNonNegativeLongInput
import io.github.magisk317.smscode.domain.constant.SmsCodeConst
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun VerificationSettingsScreen(
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val repository: SettingsPreferencesRepository = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsViewModel = rememberSharedSettingsViewModel()
    val notifySaved = {
        scope.launch {
            snackbarHostState.showLatestSnackbar(savedSnackbarText)
        }
    }
    val accordionMode = rememberPrefBoolean(PrefConst.KEY_SETTINGS_ACCORDION_MODE, true)
    var settings by remember { mutableStateOf<VerificationSettingsSnapshot?>(null) }
    var recordSettings by remember { mutableStateOf<RecordSettingsSnapshot?>(null) }
    var showDelayDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var showNotificationOwnerDialog by remember { mutableStateOf(false) }
    var pendingEnableNotification by remember { mutableStateOf(false) }
    var pendingNotificationOwnerPermissionSelection by remember { mutableStateOf<String?>(null) }
    var pendingNotificationPermissionEnable by remember { mutableStateOf(false) }
    var showSmsTestDialog by remember { mutableStateOf(false) }
    var smsTestInput by remember { mutableStateOf("") }
    var expandRelaySection by rememberSaveable { mutableStateOf(true) }
    var expandAutoInputSection by rememberSaveable { mutableStateOf(true) }
    var expandNotificationSection by rememberSaveable { mutableStateOf(true) }
    var expandExperimentalSection by rememberSaveable { mutableStateOf(true) }
    val supportsAccessibilityAutoInput = BuildConfig.ENABLE_ACCESSIBILITY_AUTO_INPUT
    var autoInputAccessibilityEnabled by remember {
        mutableStateOf(
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context),
        )
    }
    var autoInputAccessibilityListed by remember {
        mutableStateOf(
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceListed(context),
        )
    }

    suspend fun persistNotificationOwnerSelection(owner: String, enableNotification: Boolean) {
        settings = repository.updateVerificationSettings(
            VerificationSettingsUpdate(
                notificationOwner = owner,
                showCodeNotification = if (enableNotification) true else null,
            ),
        )
        notifySaved()
    }

    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        scope.launch {
            if (
                pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP &&
                NotificationUtils.hasPostNotificationsPermission(context)
            ) {
                val enableNotification = pendingNotificationPermissionEnable
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                persistNotificationOwnerSelection(
                    owner = CodeNotificationOwner.APP,
                    enableNotification = enableNotification,
                )
            } else if (pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP) {
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                snackbarHostState.showLatestSnackbar(
                    context.getString(R.string.pref_code_notification_owner_permission_denied),
                )
            }
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        if (activityOwner != null) {
            runCatching {
                notificationSettingsLauncher.launch(intent)
            }.recoverCatching {
                notificationSettingsLauncher.launch(fallbackIntent)
            }.onFailure {
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                scope.launch {
                    snackbarHostState.showLatestSnackbar(
                        context.getString(R.string.pref_code_notification_owner_permission_denied),
                    )
                }
            }
            return
        }
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.recoverCatching {
            context.startActivity(fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            pendingNotificationOwnerPermissionSelection = null
            pendingNotificationPermissionEnable = false
            scope.launch {
                snackbarHostState.showLatestSnackbar(
                    context.getString(R.string.pref_code_notification_owner_permission_denied),
                )
            }
        }
    }

    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP) {
            scope.launch {
                val enableNotification = pendingNotificationPermissionEnable
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                persistNotificationOwnerSelection(
                    owner = CodeNotificationOwner.APP,
                    enableNotification = enableNotification,
                )
            }
        } else {
            scope.launch {
                snackbarHostState.showLatestSnackbar(
                    context.getString(R.string.pref_code_notification_owner_permission_settings_hint),
                )
            }
            openNotificationSettings()
        }
    }

    fun requestNotificationPermissionIfNeeded(enableNotification: Boolean): Boolean {
        val permissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationUtils.hasPostNotificationsPermission(context)
        if (!permissionRequired) {
            return false
        }
        pendingNotificationOwnerPermissionSelection = CodeNotificationOwner.APP
        pendingNotificationPermissionEnable = enableNotification
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }

    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        autoInputAccessibilityEnabled =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
        autoInputAccessibilityListed =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceListed(context)
    }

    fun openAccessibilitySettings() {
        val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        val targetIntent = if (
            supportsAccessibilityAutoInput &&
            isAutoInputAccessibilityServiceDeclared(context) &&
            !isAutoInputAccessibilityServiceListed(context)
        ) {
            scope.launch {
                snackbarHostState.showLatestSnackbar(
                    context.getString(R.string.pref_auto_input_accessibility_service_restricted_hint),
                )
            }
            appDetailsIntent
        } else {
            accessibilityIntent
        }
        if (activityOwner != null) {
            runCatching {
                accessibilitySettingsLauncher.launch(targetIntent)
            }.onFailure {
                scope.launch {
                    snackbarHostState.showLatestSnackbar(
                        context.getString(R.string.pref_auto_input_accessibility_service_open_failed),
                    )
                }
            }
            return
        }
        runCatching {
            context.startActivity(targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            scope.launch {
                snackbarHostState.showLatestSnackbar(
                    context.getString(R.string.pref_auto_input_accessibility_service_open_failed),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        settings = repository.getVerificationSettings()
        recordSettings = repository.getRecordSettings()
        autoInputAccessibilityEnabled =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
        autoInputAccessibilityListed =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceListed(context)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            autoInputAccessibilityEnabled =
                supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
            autoInputAccessibilityListed =
                supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceListed(context)
            if (
                pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP &&
                NotificationUtils.hasPostNotificationsPermission(context)
            ) {
                val enableNotification = pendingNotificationPermissionEnable
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                persistNotificationOwnerSelection(
                    owner = CodeNotificationOwner.APP,
                    enableNotification = enableNotification,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.pref_verification_settings_title)) },
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
        val currentRecordSettings = recordSettings ?: return@Scaffold
        val historyEntries = stringArrayResource(id = R.array.history_limit_entry_list)
        val historyValues = stringArrayResource(id = R.array.history_limit_value_list)
        val retentionEntries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
        val retentionValues = stringArrayResource(id = R.array.notification_retention_time_list)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
        ) {
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
            StateSwitchItem(
                title = stringResource(id = R.string.pref_verification_features_title),
                summary = stringResource(id = R.string.pref_verification_features_summary),
                checked = current.verificationFeaturesEnabled,
                modifier = Modifier.padding(horizontal = Const.PADDING_SMALL.dp),
            ) { enabled ->
                scope.launch {
                    settings = repository.updateVerificationSettings(
                        VerificationSettingsUpdate(verificationFeaturesEnabled = enabled),
                    )
                    notifySaved()
                }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_relay),
                accordionMode = accordionMode.value,
                sectionExpanded = if (accordionMode.value) expandRelaySection else true,
                onExpandedChange = { expandRelaySection = !expandRelaySection },
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_copy_to_clipboard_title),
                    summary = stringResource(id = R.string.pref_copy_to_clipboard_summary),
                    checked = current.copyToClipboard,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(copyToClipboard = enabled))
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_relay_keywords_title),
                    summary = stringResource(id = R.string.pref_relay_keywords_summary),
                ) { showKeywordsDialog = true }
                Item(
                    title = stringResource(id = R.string.pref_relay_test_title),
                    summary = stringResource(id = R.string.pref_relay_test_summary),
                ) { showSmsTestDialog = true }
                Item(
                    title = stringResource(id = R.string.pref_code_rules_title),
                    summary = stringResource(id = R.string.pref_code_rules_summary),
                ) { onOpenRules() }
                Item(
                    title = stringResource(
                        id = R.string.pref_history_limit_title_with_target,
                        stringResource(id = R.string.record_settings_target_code),
                    ),
                    summary = stringResource(
                        id = R.string.pref_history_limit_summary,
                        historyLimitEntryLabel(
                            currentRecordSettings.codeHistoryLimit,
                            historyValues,
                            historyEntries,
                        ),
                    ),
                ) { onOpenRecords() }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_auto_input),
                accordionMode = accordionMode.value,
                sectionExpanded = if (accordionMode.value) expandAutoInputSection else true,
                onExpandedChange = { expandAutoInputSection = !expandAutoInputSection },
            ) {
                if (supportsAccessibilityAutoInput) {
                    StateSwitchItem(
                        title = stringResource(id = R.string.pref_auto_input_accessibility_service_title),
                        summary = stringResource(id = R.string.pref_auto_input_accessibility_service_summary),
                        checked = autoInputAccessibilityEnabled,
                        onTitleClick = ::openAccessibilitySettings,
                    ) {
                        openAccessibilitySettings()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_enable_auto_input_code_title),
                    summary = stringResource(id = R.string.pref_enable_auto_input_code_summary),
                    checked = current.autoInputEnabled,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(autoInputEnabled = enabled))
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_enable_auto_enter_code_title),
                    summary = stringResource(id = R.string.pref_enable_auto_enter_code_summary),
                    checked = current.autoEnterEnabled,
                    enabled = current.verificationFeaturesEnabled && current.autoInputEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(autoEnterEnabled = enabled))
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_auto_input_code_delay_title),
                    summary = stringResource(id = R.string.pref_auto_input_code_delay_summary, current.autoInputDelay),
                ) { showDelayDialog = true }
                Item(
                    title = stringResource(id = R.string.pref_auto_input_code_interval_title),
                    summary = stringResource(id = R.string.pref_auto_input_code_interval_summary, current.autoInputInterval),
                ) { showIntervalDialog = true }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_notification),
                accordionMode = accordionMode.value,
                sectionExpanded = if (accordionMode.value) expandNotificationSection else true,
                onExpandedChange = { expandNotificationSection = !expandNotificationSection },
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_show_toast_title),
                    summary = stringResource(id = R.string.pref_show_toast_summary),
                    checked = current.showToast,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(showToast = enabled))
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_show_code_notification_title),
                    summary = stringResource(id = R.string.pref_show_code_notification_summary),
                    checked = current.showCodeNotification,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    if (!enabled) {
                        scope.launch {
                            settings = repository.updateVerificationSettings(
                                VerificationSettingsUpdate(showCodeNotification = false),
                            )
                            notifySaved()
                        }
                        return@StateSwitchItem
                    }
                    pendingEnableNotification = true
                    showNotificationOwnerDialog = true
                }
                Item(
                    title = stringResource(id = R.string.pref_code_notification_owner_title),
                    summary = codeNotificationOwnerItemSummary(current.notificationOwner),
                    enabled = current.verificationFeaturesEnabled,
                ) {
                    pendingEnableNotification = false
                    showNotificationOwnerDialog = true
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_auto_cancel_notification_title),
                    summary = stringResource(id = R.string.pref_auto_cancel_notification_summary),
                    checked = current.autoCancelNotification,
                    enabled = current.verificationFeaturesEnabled && current.showCodeNotification,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(
                            VerificationSettingsUpdate(autoCancelNotification = enabled),
                        )
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_notification_retention_time_title),
                    summary = notificationRetentionEntryLabel(
                        current.notificationRetentionTime,
                        retentionValues,
                        retentionEntries,
                    ),
                    enabled = current.verificationFeaturesEnabled && current.showCodeNotification,
                ) { showRetentionDialog = true }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_experimental),
                accordionMode = accordionMode.value,
                sectionExpanded = if (accordionMode.value) expandExperimentalSection else true,
                onExpandedChange = { expandExperimentalSection = !expandExperimentalSection },
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_block_sms_title),
                    summary = stringResource(id = R.string.pref_block_sms_summary),
                    checked = current.blockSmsEnabled,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(blockSmsEnabled = enabled))
                        notifySaved()
                    }
                }
            }
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
        }
    }

    val current = settings
    if (showDelayDialog && current != null) {
        val nonNegativeNumberError = stringResource(id = R.string.pref_number_non_negative_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_delay_title),
            initialValue = normalizeIntegerInput(current.autoInputDelay),
            onDismiss = { showDelayDialog = false },
            supportingText = stringResource(id = R.string.pref_number_non_negative_integer_hint),
            validator = {
                if (parseNonNegativeLongInput(it) != null) null else nonNegativeNumberError
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            inputFilter = ::filterNonNegativeIntegerInput,
        ) { updated ->
            showDelayDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(
                    VerificationSettingsUpdate(
                        autoInputDelay = normalizeIntegerInput(updated),
                    ),
                )
                notifySaved()
            }
        }
    }
    if (showIntervalDialog && current != null) {
        val nonNegativeNumberError = stringResource(id = R.string.pref_number_non_negative_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_interval_title),
            initialValue = normalizeIntegerInput(current.autoInputInterval),
            onDismiss = { showIntervalDialog = false },
            supportingText = stringResource(id = R.string.pref_number_non_negative_integer_hint),
            validator = {
                if (parseNonNegativeLongInput(it) != null) null else nonNegativeNumberError
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            inputFilter = ::filterNonNegativeIntegerInput,
        ) { updated ->
            showIntervalDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(
                    VerificationSettingsUpdate(
                        autoInputInterval = normalizeIntegerInput(updated),
                    ),
                )
                notifySaved()
            }
        }
    }
    if (showRetentionDialog && current != null) {
        val entries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
        val values = stringArrayResource(id = R.array.notification_retention_time_list)
        SingleChoiceDialog(
            title = stringResource(id = R.string.pref_notification_retention_time_title),
            options = entries.toList(),
            selectedIndex = values.indexOf(current.notificationRetentionTime).coerceAtLeast(0),
            onDismiss = { showRetentionDialog = false },
        ) { index ->
            showRetentionDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(
                    VerificationSettingsUpdate(notificationRetentionTime = values[index]),
                )
                notifySaved()
            }
        }
    }
    if (showKeywordsDialog && current != null) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_relay_keywords_title),
            initialValue = current.relayKeywords,
            onDismiss = { showKeywordsDialog = false },
            supportingText = stringResource(id = R.string.pref_relay_keywords_summary),
            singleLine = false,
            maxLines = 8,
            resetValue = SmsCodeConst.VERIFICATION_KEYWORDS_REGEX,
        ) { updated ->
            showKeywordsDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(VerificationSettingsUpdate(relayKeywords = updated))
                notifySaved()
            }
        }
    }
    if (showSmsTestDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_relay_test_title),
            initialValue = smsTestInput,
            onDismiss = { showSmsTestDialog = false },
            singleLine = false,
            maxLines = 6,
        ) { updated ->
            settingsViewModel.performSmsCodeTest(updated)
            smsTestInput = ""
            showSmsTestDialog = false
        }
    }
    if (showNotificationOwnerDialog && current != null) {
        val options = listOf(
            stringResource(id = R.string.pref_code_notification_owner_app_option),
            stringResource(id = R.string.pref_code_notification_owner_phone_option),
        )
        val selectedIndex = when (current.notificationOwner) {
            CodeNotificationOwner.PHONE -> 1
            CodeNotificationOwner.APP -> 0
            else -> 0
        }
        SingleChoiceDialog(
            title = stringResource(id = R.string.pref_code_notification_owner_title),
            options = options,
            selectedIndex = selectedIndex,
            onDismiss = {
                showNotificationOwnerDialog = false
                pendingEnableNotification = false
            },
        ) { index ->
            val owner = if (index == 1) {
                CodeNotificationOwner.PHONE
            } else {
                CodeNotificationOwner.APP
            }
            val enableNotification = pendingEnableNotification
            showNotificationOwnerDialog = false
            pendingEnableNotification = false
            if (owner == CodeNotificationOwner.APP &&
                requestNotificationPermissionIfNeeded(enableNotification)
            ) {
                return@SingleChoiceDialog
            }
            scope.launch {
                persistNotificationOwnerSelection(owner, enableNotification)
            }
        }
    }
}

private fun historyLimitEntryLabel(
    value: String,
    values: Array<String>,
    entries: Array<String>,
): String {
    val index = values.indexOf(value)
    if (index >= 0) {
        return entries[index]
    }
    return value.takeIf { it.isNotBlank() } ?: "0"
}

@Composable
private fun codeNotificationOwnerItemSummary(owner: String): String {
    val ownerLabel = when (owner) {
        CodeNotificationOwner.PHONE -> stringResource(id = R.string.pref_code_notification_owner_phone)
        CodeNotificationOwner.APP -> stringResource(id = R.string.pref_code_notification_owner_app)
        else -> stringResource(id = R.string.pref_code_notification_owner_unselected)
    }
    return stringResource(id = R.string.pref_code_notification_owner_summary, ownerLabel)
}

private fun notificationRetentionEntryLabel(
    value: String,
    values: Array<String>,
    entries: Array<String>,
): String {
    val index = values.indexOf(value)
    if (index >= 0) {
        return entries[index]
    }
    return value.takeIf { it.isNotBlank() } ?: "0"
}

private const val AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME =
    "io.github.magisk317.relay.service.AutoInputAccessibilityService"

private fun isAutoInputAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expectedService = ComponentName(
        context.packageName,
        AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
    ).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    if (enabledServices.isBlank()) return false
    return enabledServices.split(':').any { candidate ->
        candidate.equals(expectedService, ignoreCase = true)
    }
}

private fun isAutoInputAccessibilityServiceDeclared(context: android.content.Context): Boolean {
    val componentName = ComponentName(
        context.packageName,
        AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
    )
    val packageManager = context.packageManager
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getServiceInfo(
                componentName,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getServiceInfo(componentName, PackageManager.GET_META_DATA)
        }
    }.isSuccess
}

private fun isAutoInputAccessibilityServiceListed(context: android.content.Context): Boolean {
    val expectedComponent = ComponentName(
        context.packageName,
        AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
    )
    val accessibilityManager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    return accessibilityManager.getInstalledAccessibilityServiceList().any { serviceInfo ->
        val resolvedServiceInfo = serviceInfo.resolveInfo?.serviceInfo ?: return@any false
        resolvedServiceInfo.packageName == expectedComponent.packageName &&
            resolvedServiceInfo.name == expectedComponent.className
    }
}
