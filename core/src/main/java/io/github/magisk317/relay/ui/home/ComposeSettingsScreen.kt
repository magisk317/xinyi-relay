package io.github.magisk317.relay.ui.home

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.common.constant.Const
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.AppPreferencesDataStore
import io.github.magisk317.relay.common.utils.ModuleUtils
import io.github.magisk317.relay.common.utils.PackageUtils
import io.github.magisk317.relay.common.utils.RuntimeLogStore
import io.github.magisk317.relay.common.utils.SPUtils
import io.github.magisk317.relay.common.utils.Utils
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.ui.common.LoadingIndicatorTokens
import io.github.magisk317.relay.ui.common.PolygonMorphLoadingIndicator
import io.github.magisk317.relay.ui.common.SessionLoadingRegistry
import io.github.magisk317.relay.ui.common.rememberMinDurationLoading
import io.github.magisk317.relay.ui.privacy.PrivacyPolicyPage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun ComposeSettingsScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val settingsViewModel = viewModel ?: if (activityOwner != null) {
        koinViewModel(viewModelStoreOwner = activityOwner)
    } else {
        koinViewModel()
    }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()
    val themeMode = themeState.mode

    var autoInputDelay by remember { mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT) }
    var autoInputInterval by remember { mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT) }
    var retentionTime by remember { mutableStateOf(PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT) }
    val showCodeNotificationEnabled = remember { mutableStateOf(true) }
    var smsCodeKeywords by remember { mutableStateOf(PrefConst.RELAY_KEYWORDS_DEFAULT) }
    var rootDbCatchupIntervalMin by remember { mutableStateOf("5") }
    var showAutoInputDialog by remember { mutableStateOf(false) }
    var showAutoInputIntervalDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showRootDbCatchupIntervalDialog by remember { mutableStateOf(false) }
    var showRuntimeLogFileSizeDialog by remember { mutableStateOf(false) }
    var showSmsTestDialog by remember { mutableStateOf(false) }
    var smsTestInput by remember { mutableStateOf("") }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showAlipayChoiceDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyPage by remember { mutableStateOf(false) }
    var showVerboseLogViewer by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var isActivated by remember { mutableStateOf(ModuleUtils.isModuleActivated(context)) }
    var settingsDataLoaded by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var expandGeneral by remember { mutableStateOf(false) }
    var expandSmsCode by remember { mutableStateOf(false) }
    var expandAutoInput by remember { mutableStateOf(false) }
    var expandNotification by remember { mutableStateOf(false) }
    var expandBackgroundKeepAlive by remember { mutableStateOf(false) }
    var expandExperimental by remember { mutableStateOf(false) }
    var expandOthers by remember { mutableStateOf(false) }
    val launcherIconVisible = remember { mutableStateOf(settingsViewModel.isLauncherIconVisible()) }

    val reloadSettingsData: suspend () -> Unit = {
        autoInputDelay = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
        )
        autoInputInterval = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
        )
        retentionTime = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
        )
        showCodeNotificationEnabled.value = AppPreferencesDataStore.getBoolean(
            context,
            PrefConst.KEY_SHOW_CODE_NOTIFICATION,
            true,
        )
        smsCodeKeywords = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_RELAY_KEYWORDS,
            PrefConst.RELAY_KEYWORDS_DEFAULT,
        )
        rootDbCatchupIntervalMin = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN,
            "5",
        )
        val launcherVisible = settingsViewModel.isLauncherIconVisible()
        launcherIconVisible.value = launcherVisible
        val storedLauncherVisible = AppPreferencesDataStore.getBoolean(
            context,
            PrefConst.KEY_SHOW_LAUNCHER_ICON,
            true,
        )
        if (storedLauncherVisible != launcherVisible) {
            AppPreferencesDataStore.setBoolean(
                context,
                PrefConst.KEY_SHOW_LAUNCHER_ICON,
                launcherVisible,
            )
            AppPreferencesDataStore.syncToSharedPrefs(context)
        }
        settingsViewModel.setInternalFilesWritable()
        settingsDataLoaded = true
    }

    suspend fun runManualRefresh() {
        val startedAt = SystemClock.elapsedRealtime()
        manualRefreshing = true
        reloadSettingsData()
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        manualRefreshing = false
    }

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var backupFlags by remember { mutableStateOf(BackupSelectionFlags()) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val pickedUri = data?.data ?: data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        XLog.i(
            "Backup picker result: code=%d uri=%s clipCount=%d",
            result.resultCode,
            pickedUri?.toString() ?: "<null>",
            data?.clipData?.itemCount ?: 0,
        )
        if (result.resultCode == android.app.Activity.RESULT_OK && pickedUri != null) {
            settingsViewModel.performBackup(
                pickedUri,
                backupFlags.includeConfig,
                backupFlags.includeRules,
                backupFlags.includeRecords,
                backupFlags.includeDatabase,
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val pickedUri = data?.data ?: data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        XLog.i(
            "Restore picker result: code=%d uri=%s clipCount=%d",
            result.resultCode,
            pickedUri?.toString() ?: "<null>",
            data?.clipData?.itemCount ?: 0,
        )
        if (result.resultCode == android.app.Activity.RESULT_OK && pickedUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    pickedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure {
                XLog.w(
                    "takePersistableUriPermission failed: uri=%s err=%s",
                    pickedUri.toString(),
                    it.message ?: it.javaClass.simpleName,
                )
            }
            // Show restore confirm dialog directly to avoid one-shot event loss
            // when Activity lifecycle transitions around document picker return.
            restoreUri = pickedUri
            showRestoreDialog = true
            XLog.i("Restore confirm dialog requested directly: uri=%s", pickedUri.toString())
        } else if (result.resultCode == android.app.Activity.RESULT_OK) {
            XLog.w("Restore picker returned OK but uri is null")
        }
    }

    LaunchedEffect(Unit) {
        reloadSettingsData()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            isActivated = ModuleUtils.isModuleActivated(context)
            delay(1000L)
            isActivated = ModuleUtils.isModuleActivated(context)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val markPrefsSaved = {
        Toast.makeText(context, context.getString(R.string.pref_sync_toast), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(settingsViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            settingsViewModel.eventsFlow.collect { event ->
                handleSettingsEvent(
                    event = event,
                    context = context,
                    activity = activityOwner ?: (context as? Activity),
                    scope = scope,
                    onShowPrivacyPolicy = {},
                    onShowDonate = { showDonateDialog = true },
                    onShowRestoreConfirm = { uri ->
                        restoreUri = uri
                        showRestoreDialog = true
                    },
                )
            }
        }
    }

    val scrollState = rememberScrollState()
    val showTopDivider by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("settings") }
    val showLoading = rememberMinDurationLoading(
        actualLoading = shouldShowInitialLoading && !settingsDataLoaded,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )
    val pullToRefreshState = rememberPullToRefreshState()
    val blurRadius = rememberPrefInt(PrefConst.KEY_HAZE_BLUR_RADIUS, 25)
    val tintAlpha = rememberPrefFloat(PrefConst.KEY_HAZE_TINT_ALPHA, 0.2f)
    val runtimeLogFileSizeMb = rememberPrefInt(
        PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB,
        PrefConst.RUNTIME_LOG_FILE_SIZE_MB_DEFAULT,
    )
    var showBlurRadiusDialog by remember { mutableStateOf(false) }
    var showTintAlphaDialog by remember { mutableStateOf(false) }
    val autoInputEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true)
    val autoUpdateEnabled = rememberPrefBoolean(PrefConst.KEY_AUTO_UPDATE_ON_START, true)
    val autoCancelNotificationEnabled = rememberPrefBoolean(PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false)
    val moduleEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE, true)
    val accordionMode = rememberPrefBoolean(PrefConst.KEY_SETTINGS_ACCORDION_MODE, true)
    val rootDbCatchupEnabled = rememberPrefBoolean(PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE, true)
    val forceStopRecoveryEnabled = rememberPrefBoolean(PrefConst.KEY_FORCE_STOP_RECOVERY, false)
    val verboseLogEnabled = rememberPrefBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, false)

    LaunchedEffect(autoInputEnabled.value) {
        if (!autoInputEnabled.value) {
            showAutoInputDialog = false
            showAutoInputIntervalDialog = false
        }
    }

    LaunchedEffect(showCodeNotificationEnabled.value, autoCancelNotificationEnabled.value) {
        if (!showCodeNotificationEnabled.value || !autoCancelNotificationEnabled.value) {
            showRetentionDialog = false
        }
    }

    LaunchedEffect(rootDbCatchupEnabled.value) {
        if (!rootDbCatchupEnabled.value) {
            showRootDbCatchupIntervalDialog = false
        }
    }

    LaunchedEffect(verboseLogEnabled.value) {
        if (!verboseLogEnabled.value) {
            showRuntimeLogFileSizeDialog = false
        }
    }

    LaunchedEffect(settingsDataLoaded, showLoading, shouldShowInitialLoading) {
        if (shouldShowInitialLoading && settingsDataLoaded && !showLoading) {
            SessionLoadingRegistry.markShown("settings")
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            runManualRefresh()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            Const.TOP_BAR_HEIGHT.dp // TopBar height
        val isCompact = LocalConfiguration.current.screenWidthDp < 600
        val bottomPadding =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                if (isCompact) Const.BOTTOM_SPACE_HEIGHT.dp else 0.dp

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = manualRefreshing,
            onRefresh = {
                scope.launch { runManualRefresh() }
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    isRefreshing = manualRefreshing,
                    state = pullToRefreshState,
                )
            },
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (showLoading && !manualRefreshing) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PolygonMorphLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .padding(bottom = bottomPadding)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
                ) {
                    Spacer(modifier = Modifier.height(topPadding))

                    SwitchItem(
                        title = stringResource(id = R.string.pref_enable_title),
                        summary = stringResource(id = R.string.pref_enable_summary),
                        key = PrefConst.KEY_ENABLE,
                        defaultValue = true,
                        stateOverride = moduleEnabled,
                        modifier = Modifier.padding(horizontal = Const.PADDING_SMALL.dp),
                        onSaved = markPrefsSaved,
                    )
                    SwitchItem(
                        title = stringResource(id = R.string.pref_settings_display_mode_title),
                        summary = stringResource(id = R.string.pref_settings_display_mode_summary),
                        key = PrefConst.KEY_SETTINGS_ACCORDION_MODE,
                        defaultValue = true,
                        stateOverride = accordionMode,
                        modifier = Modifier.padding(horizontal = Const.PADDING_SMALL.dp),
                        onSaved = markPrefsSaved,
                    )

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_general),
                        expanded = expandGeneral,
                        onExpandedChange = { expandGeneral = !expandGeneral },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = R.string.pref_show_launcher_icon_title),
                            summary = stringResource(id = R.string.pref_show_launcher_icon_summary),
                            key = PrefConst.KEY_SHOW_LAUNCHER_ICON,
                            defaultValue = true,
                            stateOverride = launcherIconVisible,
                            onToggle = { visible ->
                                val success = settingsViewModel.setLauncherIconVisible(visible)
                                if (!success) {
                                    launcherIconVisible.value = !visible
                                    scope.launch {
                                        AppPreferencesDataStore.setBoolean(
                                            context,
                                            PrefConst.KEY_SHOW_LAUNCHER_ICON,
                                            !visible,
                                        )
                                        AppPreferencesDataStore.syncToSharedPrefs(context)
                                    }
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.pref_show_launcher_icon_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onSaved = markPrefsSaved,
                        )
                        Item(
                            title = stringResource(id = R.string.pref_choose_theme_title),
                            summary = stringResource(id = R.string.pref_choose_theme_summary),
                        ) { showThemeDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_language_title),
                            summary = stringResource(id = R.string.pref_language_summary),
                        ) { showLanguageDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_haze_blur_radius_title),
                            summary = "${blurRadius.intValue}dp",
                        ) { showBlurRadiusDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_haze_tint_alpha_title),
                            summary = "%.2f".format(tintAlpha.floatValue),
                        ) { showTintAlphaDialog = true }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_relay),
                        expanded = expandSmsCode,
                        onExpandedChange = { expandSmsCode = !expandSmsCode },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = R.string.pref_copy_to_clipboard_title),
                            summary = stringResource(id = R.string.pref_copy_to_clipboard_summary),
                            key = PrefConst.KEY_COPY_TO_CLIPBOARD,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                        Item(
                            title = stringResource(id = R.string.pref_relay_keywords_title),
                            summary = stringResource(id = R.string.pref_relay_keywords_summary),
                        ) { showKeywordsDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_relay_test_title),
                            summary = stringResource(id = R.string.pref_relay_test_summary),
                        ) { showSmsTestDialog = true }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_auto_input),
                        expanded = expandAutoInput,
                        onExpandedChange = { expandAutoInput = !expandAutoInput },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = R.string.pref_enable_auto_input_code_title),
                            summary = stringResource(id = R.string.pref_enable_auto_input_code_summary),
                            key = PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
                            defaultValue = true,
                            stateOverride = autoInputEnabled,
                            onSaved = markPrefsSaved,
                        )
                        if (autoInputEnabled.value) {
                            SwitchItem(
                                title = stringResource(id = R.string.pref_enable_auto_enter_code_title),
                                summary = stringResource(id = R.string.pref_enable_auto_enter_code_summary),
                                key = PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
                                defaultValue = false,
                                onSaved = markPrefsSaved,
                            )
                            Item(
                                title = stringResource(id = R.string.pref_auto_input_code_delay_title),
                                summary = stringResource(id = R.string.pref_auto_input_code_delay_summary, autoInputDelay),
                            ) { showAutoInputDialog = true }
                            Item(
                                title = stringResource(id = R.string.pref_auto_input_code_interval_title),
                                summary = stringResource(
                                    id = R.string.pref_auto_input_code_interval_summary,
                                    autoInputInterval,
                                ),
                            ) { showAutoInputIntervalDialog = true }
                        }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_notification),
                        expanded = expandNotification,
                        onExpandedChange = { expandNotification = !expandNotification },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = R.string.pref_show_toast_title),
                            summary = stringResource(id = R.string.pref_show_toast_summary),
                            key = PrefConst.KEY_SHOW_TOAST,
                            defaultValue = true,
                            onSaved = markPrefsSaved,
                        )
                        SwitchItem(
                            title = stringResource(id = R.string.pref_show_code_notification_title),
                            summary = stringResource(id = R.string.pref_show_code_notification_summary),
                            key = PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                            defaultValue = true,
                            stateOverride = showCodeNotificationEnabled,
                            onSaved = markPrefsSaved,
                        )
                        if (showCodeNotificationEnabled.value) {
                            SwitchItem(
                                title = stringResource(id = R.string.pref_auto_cancel_notification_title),
                                summary = stringResource(id = R.string.pref_auto_cancel_notification_summary),
                                key = PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                                defaultValue = false,
                                stateOverride = autoCancelNotificationEnabled,
                                onSaved = markPrefsSaved,
                            )
                            if (autoCancelNotificationEnabled.value) {
                                Item(
                                    title = stringResource(id = R.string.pref_notification_retention_time_title),
                                    summary = run {
                                        val entries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
                                        val values = stringArrayResource(id = R.array.notification_retention_time_list)
                                        val index = values.indexOf(retentionTime)
                                        if (index >= 0) entries[index] else retentionTime
                                    },
                                ) { showRetentionDialog = true }
                            }
                        }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_background_keepalive),
                        expanded = expandBackgroundKeepAlive,
                        onExpandedChange = { expandBackgroundKeepAlive = !expandBackgroundKeepAlive },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = R.string.pref_root_db_catchup_enable_title),
                            summary = stringResource(id = R.string.pref_root_db_catchup_enable_summary),
                            key = PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE,
                            defaultValue = true,
                            stateOverride = rootDbCatchupEnabled,
                            onSaved = markPrefsSaved,
                        )
                        if (rootDbCatchupEnabled.value) {
                            Item(
                                title = stringResource(id = R.string.pref_root_db_catchup_interval_title),
                                summary = stringResource(
                                    id = R.string.pref_root_db_catchup_interval_summary,
                                    rootDbCatchupIntervalMin,
                                ),
                            ) { showRootDbCatchupIntervalDialog = true }
                            SwitchItem(
                                title = stringResource(id = R.string.pref_root_db_catchup_writeback_title),
                                summary = stringResource(id = R.string.pref_root_db_catchup_writeback_summary),
                                key = PrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK,
                                defaultValue = false,
                                onSaved = markPrefsSaved,
                            )
                        }
                        SwitchItem(
                            title = stringResource(id = R.string.pref_force_stop_recovery_title),
                            summary = stringResource(id = R.string.pref_force_stop_recovery_summary),
                            key = PrefConst.KEY_FORCE_STOP_RECOVERY,
                            defaultValue = false,
                            stateOverride = forceStopRecoveryEnabled,
                            onSaved = markPrefsSaved,
                        )
                        if (forceStopRecoveryEnabled.value) {
                            SwitchItem(
                                title = stringResource(id = R.string.pref_force_stop_recovery_relaunch_once_title),
                                summary = stringResource(id = R.string.pref_force_stop_recovery_relaunch_once_summary),
                                key = PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
                                defaultValue = false,
                                onSaved = markPrefsSaved,
                            )
                        }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_experimental),
                        expanded = expandExperimental,
                        onExpandedChange = { expandExperimental = !expandExperimental },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = R.string.pref_block_sms_title),
                            summary = stringResource(id = R.string.pref_block_sms_summary),
                            key = PrefConst.KEY_BLOCK_SMS,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_others),
                        expanded = expandOthers,
                        onExpandedChange = { expandOthers = !expandOthers },
                        accordionMode = accordionMode.value,
                    ) {
                        Item(
                            title = stringResource(id = R.string.pref_backup_title),
                            summary = stringResource(id = R.string.pref_backup_summary),
                        ) { showBackupDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_restore_title),
                            summary = stringResource(id = R.string.pref_restore_summary),
                        ) {
                            val intent = io.github.magisk317.relay.feature.backup.BackupManager.getImportRuleListSAFIntent(context)
                            restoreLauncher.launch(intent)
                        }
                        SwitchItem(
                            title = stringResource(id = R.string.pref_verbose_log_mode_title),
                            summary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                            key = PrefConst.KEY_VERBOSE_LOG_MODE,
                            defaultValue = false,
                            stateOverride = verboseLogEnabled,
                            onItemClick = { showVerboseLogViewer = true },
                            onToggle = { on ->
                                RuntimeLogStore.setEnabled(on)
                                XLog.setLogLevel(if (on) Log.VERBOSE else io.github.magisk317.relay.storage.BuildConfig.LOG_LEVEL)
                            },
                            onSaved = markPrefsSaved,
                        )
                        if (verboseLogEnabled.value) {
                            Item(
                                title = stringResource(id = R.string.pref_runtime_log_file_size_title),
                                summary = stringResource(
                                    id = R.string.pref_runtime_log_file_size_summary,
                                    runtimeLogFileSizeMb.intValue,
                                ),
                            ) { showRuntimeLogFileSizeDialog = true }
                        }
                        SwitchItem(
                            title = stringResource(id = R.string.pref_auto_update_on_start_title),
                            summary = stringResource(id = R.string.pref_auto_update_on_start_summary),
                            key = PrefConst.KEY_AUTO_UPDATE_ON_START,
                            defaultValue = true,
                            stateOverride = autoUpdateEnabled,
                            onSaved = markPrefsSaved,
                        )
                        if (autoUpdateEnabled.value) {
                            SwitchItem(
                                title = stringResource(id = R.string.pref_auto_update_wifi_only_title),
                                summary = stringResource(id = R.string.pref_auto_update_wifi_only_summary),
                                key = PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
                                defaultValue = false,
                                onSaved = markPrefsSaved,
                            )
                        }
                        Item(
                            title = stringResource(id = R.string.pref_privacy_policy_title),
                            summary = "",
                        ) { showPrivacyPolicyPage = true }
                    }

                    Spacer(modifier = Modifier.height(Const.SPACING_SMALL.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.pref_general_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier
                    .hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }

    SettingsDialogs(
        context = context,
        scope = scope,
        themeMode = themeMode,
        autoInputDelay = autoInputDelay,
        autoInputInterval = autoInputInterval,
        retentionTime = retentionTime,
        smsTestInput = smsTestInput,
        smsCodeKeywords = smsCodeKeywords,
        showAutoInputDialog = showAutoInputDialog,
        showAutoInputIntervalDialog = showAutoInputIntervalDialog,
        showRetentionDialog = showRetentionDialog,
        showSmsTestDialog = showSmsTestDialog,
        showKeywordsDialog = showKeywordsDialog,
        showThemeDialog = showThemeDialog,
        showDonateDialog = showDonateDialog,
        showAlipayChoiceDialog = showAlipayChoiceDialog,
        showQRCodeDialog = showQRCodeDialog,
        showPrivacyPolicyDialog = showPrivacyPolicyDialog,
        showPrivacyPolicyPage = showPrivacyPolicyPage,
        showBackupDialog = showBackupDialog,
        showRestoreDialog = showRestoreDialog,
        restoreUri = restoreUri,
        onAutoInputDelayChange = { autoInputDelay = it },
        onAutoInputIntervalChange = { autoInputInterval = it },
        onRetentionTimeChange = { retentionTime = it },
        onSmsTestInputChange = { smsTestInput = it },
        onSmsKeywordsChange = { smsCodeKeywords = it },
        onShowAutoInputDialogChange = { showAutoInputDialog = it },
        onShowAutoInputIntervalDialogChange = { showAutoInputIntervalDialog = it },
        onShowRetentionDialogChange = { showRetentionDialog = it },
        onShowSmsTestDialogChange = { showSmsTestDialog = it },
        onShowKeywordsDialogChange = { showKeywordsDialog = it },
        onShowThemeDialogChange = { showThemeDialog = it },
        onShowDonateDialogChange = { showDonateDialog = it },
        onShowAlipayChoiceDialogChange = { showAlipayChoiceDialog = it },
        onShowQrCodeDialogChange = { showQRCodeDialog = it },
        onShowPrivacyPolicyDialogChange = { showPrivacyPolicyDialog = it },
        onShowPrivacyPolicyPageChange = { showPrivacyPolicyPage = it },
        onShowBackupDialogChange = { showBackupDialog = it },
        onShowRestoreDialogChange = { showRestoreDialog = it },
        onBackupFlagsChange = { backupFlags = it },
        onPendingSavedToast = markPrefsSaved,
        backupLauncher = backupLauncher,
        settingsViewModel = settingsViewModel,
        onExit = onExit,
        onSetTheme = { mode, x, y -> settingsViewModel.setThemeMode(mode, x, y) },
    )

    if (showVerboseLogViewer) {
        RuntimeLogViewerSheet(
            onDismiss = { showVerboseLogViewer = false },
        )
    }

    if (showLanguageDialog) {
        LanguageChooserDialog(
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { tag ->
                val locales = if (tag.isEmpty()) {
                    androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                } else {
                    androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                }
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                showLanguageDialog = false
            },
        )
    }

    if (showBlurRadiusDialog) {
        SliderDialog(
            title = stringResource(id = R.string.pref_haze_blur_radius_title),
            value = blurRadius.intValue.toFloat(),
            valueRange = 0f..100f,
            steps = 0,
            onDismiss = { showBlurRadiusDialog = false },
            onValueChange = {
                val newVal = it.toInt()
                blurRadius.intValue = newVal
                scope.launch {
                    AppPreferencesDataStore.setInt(context, PrefConst.KEY_HAZE_BLUR_RADIUS, newVal)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    markPrefsSaved()
                }
                showBlurRadiusDialog = false
            },
            valueFormatter = { "${it.toInt()}dp" },
        )
    }

    if (showTintAlphaDialog) {
        SliderDialog(
            title = stringResource(id = R.string.pref_haze_tint_alpha_title),
            value = tintAlpha.floatValue,
            valueRange = 0f..1f,
            steps = 0,
            onDismiss = { showTintAlphaDialog = false },
            onValueChange = {
                tintAlpha.floatValue = it
                scope.launch {
                    AppPreferencesDataStore.setFloat(context, PrefConst.KEY_HAZE_TINT_ALPHA, it)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    markPrefsSaved()
                }
                showTintAlphaDialog = false
            },
        )
    }

    if (showRuntimeLogFileSizeDialog) {
        val runtimeLogFileSizeErrorText = stringResource(id = R.string.pref_runtime_log_file_size_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_runtime_log_file_size_title),
            initialValue = runtimeLogFileSizeMb.intValue.toString(),
            supportingText = stringResource(id = R.string.pref_runtime_log_file_size_hint),
            validator = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed == null || parsed < PrefConst.RUNTIME_LOG_FILE_SIZE_MB_MIN) {
                    runtimeLogFileSizeErrorText
                } else {
                    null
                }
            },
            onDismiss = { showRuntimeLogFileSizeDialog = false },
            onConfirm = { value ->
                val normalized = value.trim().toIntOrNull()
                    ?.coerceAtLeast(PrefConst.RUNTIME_LOG_FILE_SIZE_MB_MIN)
                    ?: PrefConst.RUNTIME_LOG_FILE_SIZE_MB_DEFAULT
                runtimeLogFileSizeMb.intValue = normalized
                scope.launch {
                    AppPreferencesDataStore.setInt(
                        context,
                        PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB,
                        normalized,
                    )
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    RuntimeLogStore.setMaxFileSizeMb(normalized)
                    markPrefsSaved()
                }
                showRuntimeLogFileSizeDialog = false
            },
        )
    }

    if (showRootDbCatchupIntervalDialog) {
        val rootDbCatchupIntervalErrorText = stringResource(id = R.string.pref_root_db_catchup_interval_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_root_db_catchup_interval_title),
            initialValue = rootDbCatchupIntervalMin,
            supportingText = stringResource(id = R.string.pref_root_db_catchup_interval_hint),
            validator = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed == null || parsed !in 1..120) {
                    rootDbCatchupIntervalErrorText
                } else {
                    null
                }
            },
            onDismiss = { showRootDbCatchupIntervalDialog = false },
            onConfirm = { value ->
                val normalized = value.trim().toIntOrNull()?.coerceIn(1, 120)?.toString() ?: "5"
                rootDbCatchupIntervalMin = normalized
                scope.launch {
                    AppPreferencesDataStore.setString(
                        context,
                        PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN,
                        normalized,
                    )
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    markPrefsSaved()
                }
                showRootDbCatchupIntervalDialog = false
            },
        )
    }
}

private fun handleSettingsEvent(
    event: SettingsEvent,
    context: android.content.Context,
    activity: Activity?,
    scope: kotlinx.coroutines.CoroutineScope,
    onShowPrivacyPolicy: () -> Unit,
    onShowDonate: () -> Unit,
    onShowRestoreConfirm: (android.net.Uri) -> Unit,
) {
    when (event) {
        is SettingsEvent.SmsCodeTestResult -> {
            val text = if (event.code.isBlank()) {
                context.getString(R.string.cannot_parse_relay_code)
            } else {
                context.getString(R.string.current_sms_code, event.code)
            }
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
        }

        is SettingsEvent.ShowPrivacyPolicy -> onShowPrivacyPolicy()
        is SettingsEvent.ShowAlipayPacket -> onShowDonate()
        is SettingsEvent.BackupResultEvent -> {
            val msg = if (event.success) R.string.backup_success else R.string.backup_failed
            android.widget.Toast.makeText(context, context.getString(msg), android.widget.Toast.LENGTH_SHORT).show()
        }

        is SettingsEvent.RestoreResultEvent -> {
            val msg = when (event.result.result) {
                io.github.magisk317.relay.feature.backup.ImportResult.SUCCESS -> R.string.restore_success
                io.github.magisk317.relay.feature.backup.ImportResult.VERSION_TOO_NEW -> R.string.import_failed_version_too_new
                io.github.magisk317.relay.feature.backup.ImportResult.VERSION_TOO_OLD -> R.string.import_failed_version_too_old
                else -> R.string.restore_failed
            }
            android.widget.Toast.makeText(context, context.getString(msg), android.widget.Toast.LENGTH_SHORT).show()

            if (event.result.result == io.github.magisk317.relay.feature.backup.ImportResult.SUCCESS) {
                Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
                scope.launch {
                    delay(1200L)
                    if (activity != null) {
                        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            activity.startActivity(intent)
                        }
                        activity.finish()
                    }
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }

        is SettingsEvent.ImportDialogConfirm -> onShowRestoreConfirm(event.uri)
        else -> Unit
    }
}

@Composable
private fun SettingsDialogs(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    themeMode: Int,
    autoInputDelay: String,
    autoInputInterval: String,
    retentionTime: String,
    smsTestInput: String,
    smsCodeKeywords: String,
    showAutoInputDialog: Boolean,
    showAutoInputIntervalDialog: Boolean,
    showRetentionDialog: Boolean,
    showSmsTestDialog: Boolean,
    showKeywordsDialog: Boolean,
    showThemeDialog: Boolean,
    showDonateDialog: Boolean,
    showAlipayChoiceDialog: Boolean,
    showQRCodeDialog: Pair<Int, String>?,
    showPrivacyPolicyDialog: Boolean,
    showPrivacyPolicyPage: Boolean,
    showBackupDialog: Boolean,
    showRestoreDialog: Boolean,
    restoreUri: android.net.Uri?,
    onAutoInputDelayChange: (String) -> Unit,
    onAutoInputIntervalChange: (String) -> Unit,
    onRetentionTimeChange: (String) -> Unit,
    onSmsTestInputChange: (String) -> Unit,
    onSmsKeywordsChange: (String) -> Unit,
    onShowAutoInputDialogChange: (Boolean) -> Unit,
    onShowAutoInputIntervalDialogChange: (Boolean) -> Unit,
    onShowRetentionDialogChange: (Boolean) -> Unit,
    onShowSmsTestDialogChange: (Boolean) -> Unit,
    onShowKeywordsDialogChange: (Boolean) -> Unit,
    onShowThemeDialogChange: (Boolean) -> Unit,
    onShowDonateDialogChange: (Boolean) -> Unit,
    onShowAlipayChoiceDialogChange: (Boolean) -> Unit,
    onShowQrCodeDialogChange: (Pair<Int, String>?) -> Unit,
    onShowPrivacyPolicyDialogChange: (Boolean) -> Unit,
    onShowPrivacyPolicyPageChange: (Boolean) -> Unit,
    onShowBackupDialogChange: (Boolean) -> Unit,
    onShowRestoreDialogChange: (Boolean) -> Unit,
    onBackupFlagsChange: (BackupSelectionFlags) -> Unit,
    onPendingSavedToast: () -> Unit,
    backupLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    settingsViewModel: SettingsViewModel,
    onExit: () -> Unit,
    onSetTheme: (Int, Float, Float) -> Unit,
) {
    if (showAutoInputDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_delay_title),
            initialValue = autoInputDelay,
            onDismiss = { onShowAutoInputDialogChange(false) },
        ) { value ->
            onAutoInputDelayChange(value)
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                onPendingSavedToast()
            }
            onShowAutoInputDialogChange(false)
        }
    }

    if (showAutoInputIntervalDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_interval_title),
            initialValue = autoInputInterval,
            onDismiss = { onShowAutoInputIntervalDialogChange(false) },
        ) { value ->
            onAutoInputIntervalChange(value)
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                onPendingSavedToast()
            }
            onShowAutoInputIntervalDialogChange(false)
        }
    }

    if (showRetentionDialog) {
        RetentionDialog(
            selectedValue = retentionTime,
            onDismiss = { onShowRetentionDialogChange(false) },
        ) { value ->
            onRetentionTimeChange(value)
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_NOTIFICATION_RETENTION_TIME, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                onPendingSavedToast()
            }
            onShowRetentionDialogChange(false)
        }
    }

    if (showSmsTestDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_relay_test_title),
            initialValue = smsTestInput,
            onDismiss = { onShowSmsTestDialogChange(false) },
            singleLine = false,
            maxLines = 8,
        ) { value ->
            onSmsTestInputChange(value)
            settingsViewModel.performSmsCodeTest(value)
            onShowSmsTestDialogChange(false)
        }
    }

    if (showKeywordsDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_relay_keywords_title),
            initialValue = smsCodeKeywords,
            onDismiss = { onShowKeywordsDialogChange(false) },
            singleLine = false,
            maxLines = 10,
            resetValue = PrefConst.RELAY_KEYWORDS_DEFAULT,
        ) { value ->
            val updated = if (value.isBlank()) PrefConst.RELAY_KEYWORDS_DEFAULT else value
            onSmsKeywordsChange(updated)
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_RELAY_KEYWORDS, updated)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                onPendingSavedToast()
            }
            onShowKeywordsDialogChange(false)
        }
    }

    if (showThemeDialog) {
        ThemeChooserDialog(
            currentMode = themeMode,
            onDismiss = { onShowThemeDialogChange(false) },
            onThemeSelected = { mode, x, y ->
                onSetTheme(mode, x, y)
                onShowThemeDialogChange(false)
            },
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { onShowDonateDialogChange(false) },
            onAlipay = {
                onShowDonateDialogChange(false)
                onShowAlipayChoiceDialogChange(true)
            },
            onWechat = {
                onShowDonateDialogChange(false)
                onShowQrCodeDialogChange(Pair(R.drawable.wx, "wechat"))
            },
        )
    }

    if (showAlipayChoiceDialog) {
        AlipayChoiceDialog(
            onDismiss = { onShowAlipayChoiceDialogChange(false) },
            onQRCode = {
                onShowAlipayChoiceDialogChange(false)
                onShowQrCodeDialogChange(Pair(R.drawable.alipay, "alipay"))
            },
            onToken = {
                onShowAlipayChoiceDialogChange(false)
                PackageUtils.copyAlipayPocketToken(context)
                PackageUtils.startAlipayActivity(context)
            },
        )
    }

    showQRCodeDialog?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { onShowQrCodeDialogChange(null) },
            onSave = { Utils.saveImageToGallery(context, pair.first, "${pair.second}_qrcode") },
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { onShowPrivacyPolicyDialogChange(false) },
            onConfirm = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, true) }
                onShowPrivacyPolicyDialogChange(false)
            },
            onCancel = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, false) }
                onShowPrivacyPolicyDialogChange(false)
                onExit()
            },
            onViewPolicy = {
                onShowPrivacyPolicyDialogChange(false)
                onShowPrivacyPolicyPageChange(true)
            },
        )
    }

    if (showPrivacyPolicyPage) {
        PrivacyPolicyPage(onDismiss = { onShowPrivacyPolicyPageChange(false) })
    }

    if (showBackupDialog) {
        BackupDialog(
            onDismiss = { onShowBackupDialogChange(false) },
            onConfirm = { flags ->
                onBackupFlagsChange(flags)
                onShowBackupDialogChange(false)
                val intent = io.github.magisk317.relay.feature.backup.BackupManager.getExportRuleListSAFIntent(
                    context,
                    includeDatabase = flags.includeDatabase,
                )
                backupLauncher.launch(intent)
            },
        )
    }

    if (showRestoreDialog && restoreUri != null) {
        RestoreConfirmDialog(
            onDismiss = { onShowRestoreDialogChange(false) },
            onConfirm = { flags ->
                settingsViewModel.performRestore(
                    uri = restoreUri,
                    restoreConfig = flags.includeConfig,
                    restoreRules = flags.includeRules,
                    restoreRecords = flags.includeRecords,
                    restoreDatabase = flags.includeDatabase,
                )
                onShowRestoreDialogChange(false)
            },
        )
    }
}

// Helper Composables (extracted and made standalone)

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = Const.PADDING_MEDIUM.dp, vertical = Const.SPACING_SMALL.dp),
    )
}

@Composable
private fun ExpandableSettingsSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    accordionMode: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sectionExpanded = if (accordionMode) expanded else true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Const.PADDING_SMALL.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    if (accordionMode) {
                        Icon(
                            imageVector = if (sectionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = accordionMode, onClick = onExpandedChange),
            )

            AnimatedVisibility(visible = sectionExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun Item(title: String, summary: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (summary.isNotEmpty()) {
            {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
fun SwitchItem(
    title: String,
    summary: String,
    key: String,
    defaultValue: Boolean,
    modifier: Modifier = Modifier,
    stateOverride: MutableState<Boolean>? = null,
    enabled: Boolean = true,
    onItemClick: (() -> Unit)? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    onSaved: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checkedState = stateOverride ?: rememberPrefBoolean(key, defaultValue)
    val defaultSavedToast = context.getString(R.string.pref_sync_toast)

    fun toggle(checked: Boolean) {
        if (!enabled) return
        checkedState.value = checked
        scope.launch {
            AppPreferencesDataStore.setBoolean(context, key, checked)
            AppPreferencesDataStore.syncToSharedPrefs(context)
            if (onSaved != null) {
                onSaved()
            } else {
                Toast.makeText(context, defaultSavedToast, Toast.LENGTH_SHORT).show()
            }
        }
        onToggle?.invoke(checked)
    }

    ListItem(
        headlineContent = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (summary.isNotEmpty()) {
            {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        trailingContent = {
            Switch(
                checked = checkedState.value,
                onCheckedChange = { toggle(it) },
                enabled = enabled,
            )
        },
        modifier = modifier.clickable(enabled = enabled) {
            if (onItemClick != null) {
                onItemClick()
            } else {
                toggle(!checkedState.value)
            }
        },
    )
}

@Composable
fun rememberPrefBoolean(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(defaultValue) }
    LaunchedEffect(key) {
        state.value = AppPreferencesDataStore.getBoolean(context, key, defaultValue)
    }
    return state
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun TextInputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    supportingText: String? = null,
    resetValue: String? = null,
    validator: ((String) -> String?)? = null,
    onFocusLost: ((String) -> Unit)? = null,
    onDismissWithValue: ((String) -> Unit)? = null,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    var hadFocus by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)
    AlertDialog(
        onDismissRequest = {
            onDismissWithValue?.invoke(text)
            onDismiss()
        },
        modifier = modifier,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                )
                if (resetValue != null) {
                    TextButton(
                        onClick = {
                            text = resetValue
                            errorMessage = validator?.invoke(resetValue)
                        },
                    ) {
                        Text(text = stringResource(id = R.string.reset))
                    }
                }
            }
        },
        text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        if (validator != null) {
                        errorMessage = validator(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            onFocusLost?.invoke(text)
                        }
                    },
                singleLine = singleLine,
                maxLines = maxLines,
                isError = errorMessage != null,
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }) {
                            Icon(imageVector = Icons.Filled.Clear, contentDescription = null)
                        }
                    }
                },
                supportingText = if (errorMessage != null || supportingText != null) {
                    { Text(text = errorMessage ?: supportingText!!) }
                } else null,
            )
        },
        confirmButton = {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clickableItem(
                    onClick = onDismiss,
                    label = cancelLabel,
                    weight = 1f,
                )
                clickableItem(
                    onClick = {
                        var hasError = false
                        if (validator != null) {
                            val error = validator(text)
                            if (error != null) {
                                errorMessage = error
                                hasError = true
                            }
                        }
                        if (!hasError) {
                            onConfirm(text)
                        }
                    },
                    label = confirmLabel,
                    weight = 1f,
                )
            }
        },
        dismissButton = {},
    )
}

@Composable
fun RetentionDialog(
    selectedValue: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    titleId: Int = R.string.pref_notification_retention_time_title,
    entriesId: Int = R.array.notification_retention_time_entry_list,
    valuesId: Int = R.array.notification_retention_time_list,
    onConfirm: (String) -> Unit,
) {
    val entries = stringArrayResource(id = entriesId)
    val values = stringArrayResource(id = valuesId)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(id = titleId)) },
        text = {
            Column {
                entries.forEachIndexed { index, entry ->
                    val value = values.getOrNull(index) ?: return@forEachIndexed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(value) }
                            .padding(vertical = Const.PADDING_MEDIUM.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selectedValue, onClick = { onConfirm(value) })
                        Text(text = entry, modifier = Modifier.padding(start = Const.SPACING_MEDIUM.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
fun ThemeChooserDialog(currentMode: Int, onDismiss: () -> Unit, onThemeSelected: (Int, Float, Float) -> Unit) {
    val modes = listOf(
        stringResource(id = R.string.theme_follow_system) to 0,
        stringResource(id = R.string.theme_light) to 1,
        stringResource(id = R.string.theme_dark) to 2,
        stringResource(id = R.string.theme_black) to 3,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.pref_choose_theme_title)) },
        text = {
            Column {
                modes.forEach { (label, mode) ->
                    var rowCoords: LayoutCoordinates? by remember { mutableStateOf(null) }
                    val view = LocalView.current

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .onGloballyPositioned { rowCoords = it }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        val locationOnScreen = IntArray(2)
                                        view.getLocationOnScreen(locationOnScreen)

                                        val rootCoords =
                                            rowCoords?.positionInRoot() ?: androidx.compose.ui.geometry.Offset.Zero

                                        // Dialog Window Offset + Item Offset in Dialog + Tap Offset
                                        val finalX = locationOnScreen[0] + rootCoords.x + tapOffset.x
                                        val finalY = locationOnScreen[1] + rootCoords.y + tapOffset.y

                                        onThemeSelected(mode, finalX, finalY)
                                    },
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == currentMode, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
fun LanguageChooserDialog(onDismiss: () -> Unit, onLanguageSelected: (String) -> Unit) {
    val context = LocalContext.current
    val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "" else currentLocales.get(0)?.toLanguageTag() ?: ""

    val languages = listOf(
        stringResource(id = R.string.language_follow_system) to "",
        stringResource(id = R.string.language_en) to "en",
        stringResource(id = R.string.language_zh_cn) to "zh-CN",
        stringResource(id = R.string.language_zh_tw) to "zh-TW",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.pref_language_title)) },
        text = {
            Column {
                languages.forEach { (label, tag) ->
                    val selected = if (tag.isEmpty()) currentTag.isEmpty() else currentTag.startsWith(tag)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(tag) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onLanguageSelected(tag) },
                        )
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
fun DonateDialog(onDismiss: () -> Unit, onAlipay: () -> Unit, onWechat: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_donate_title)) },
        text = { Text(stringResource(id = R.string.dialog_donate_content)) },
        confirmButton = {
            FilledTonalButton(onClick = onAlipay) { Text(stringResource(id = R.string.dialog_donate_alipay)) }
            OutlinedButton(onClick = onWechat) { Text(stringResource(id = R.string.dialog_donate_wechat)) }
        },
    )
}

@Composable
fun AlipayChoiceDialog(onDismiss: () -> Unit, onQRCode: () -> Unit, onToken: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_donate_alipay)) },
        confirmButton = {
            FilledTonalButton(onClick = onQRCode) { Text(stringResource(id = R.string.dialog_donate_alipay_qrcode)) }
            OutlinedButton(onClick = onToken) { Text(stringResource(id = R.string.dialog_donate_alipay_token)) }
        },
    )
}

@Composable
fun QRCodeDialog(resId: Int, type: String, onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (type == "alipay") {
                    stringResource(
                        id = R.string.dialog_donate_alipay,
                    )
                } else {
                    stringResource(id = R.string.dialog_donate_wechat)
                },
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = resId),
                    contentDescription = if (type == "alipay") {
                        stringResource(
                            id = R.string.dialog_donate_alipay,
                        )
                    } else {
                        stringResource(id = R.string.dialog_donate_wechat)
                    },
                    modifier = Modifier.size(200.dp),
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onSave) { Text(stringResource(id = R.string.save_to_gallery)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        },
    )
}

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onViewPolicy: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        title = { Text(stringResource(id = R.string.privacy_dialog_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.privacy_dialog_content))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onViewPolicy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(id = R.string.privacy_policy_button))
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.privacy_dialog_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(id = R.string.privacy_dialog_cancel))
            }
        },
    )
}

private data class BackupSelectionFlags(
    val includeConfig: Boolean = true,
    val includeRules: Boolean = true,
    val includeRecords: Boolean = true,
    val includeDatabase: Boolean = false,
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun BackupDialog(onDismiss: () -> Unit, onConfirm: (BackupSelectionFlags) -> Unit) {
    var checkConfig by remember { mutableStateOf(true) }
    var checkRules by remember { mutableStateOf(true) }
    var checkRecords by remember { mutableStateOf(true) }
    var checkDatabase by remember { mutableStateOf(false) }
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_backup_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.dialog_backup_msg), modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkConfig = !checkConfig },
                ) {
                    Checkbox(checked = checkConfig, onCheckedChange = { checkConfig = it })
                    Text(stringResource(id = R.string.item_config))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRules = !checkRules },
                ) {
                    Checkbox(checked = checkRules, onCheckedChange = { checkRules = it })
                    Text(stringResource(id = R.string.item_rules))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRecords = !checkRecords },
                ) {
                    Checkbox(checked = checkRecords, onCheckedChange = { checkRecords = it })
                    Text(stringResource(id = R.string.item_records))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkDatabase = !checkDatabase },
                ) {
                    Checkbox(checked = checkDatabase, onCheckedChange = { checkDatabase = it })
                    Text(stringResource(id = R.string.item_database_with_note))
                }
            }
        },
        confirmButton = {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clickableItem(
                    onClick = onDismiss,
                    label = cancelLabel,
                    weight = 1f,
                )
                clickableItem(
                    onClick = {
                        onConfirm(
                            BackupSelectionFlags(
                                includeConfig = checkConfig,
                                includeRules = checkRules,
                                includeRecords = checkRecords,
                                includeDatabase = checkDatabase,
                            ),
                        )
                    },
                    label = confirmLabel,
                    weight = 1f,
                )
            }
        },
        dismissButton = {},
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun RestoreConfirmDialog(onDismiss: () -> Unit, onConfirm: (BackupSelectionFlags) -> Unit) {
    var checkConfig by remember { mutableStateOf(true) }
    var checkRules by remember { mutableStateOf(true) }
    var checkRecords by remember { mutableStateOf(true) }
    var checkDatabase by remember { mutableStateOf(false) }
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_restore_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.dialog_restore_msg), modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkConfig = !checkConfig },
                ) {
                    Checkbox(checked = checkConfig, onCheckedChange = { checkConfig = it })
                    Text(stringResource(id = R.string.item_config))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRules = !checkRules },
                ) {
                    Checkbox(checked = checkRules, onCheckedChange = { checkRules = it })
                    Text(stringResource(id = R.string.item_rules))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRecords = !checkRecords },
                ) {
                    Checkbox(checked = checkRecords, onCheckedChange = { checkRecords = it })
                    Text(stringResource(id = R.string.item_records))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkDatabase = !checkDatabase },
                ) {
                    Checkbox(checked = checkDatabase, onCheckedChange = { checkDatabase = it })
                    Text(stringResource(id = R.string.item_database_with_note))
                }
                Text(
                    text = stringResource(id = R.string.restore_warning_msg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clickableItem(
                    onClick = onDismiss,
                    label = cancelLabel,
                    weight = 1f,
                )
                clickableItem(
                    onClick = {
                        onConfirm(
                            BackupSelectionFlags(
                                includeConfig = checkConfig,
                                includeRules = checkRules,
                                includeRecords = checkRecords,
                                includeDatabase = checkDatabase,
                            ),
                        )
                    },
                    label = confirmLabel,
                    weight = 1f,
                )
            }
        },
        dismissButton = {},
    )
}

@Composable
fun rememberPrefInt(key: String, defaultValue: Int): MutableIntState {
    val context = LocalContext.current
    val state = remember { mutableIntStateOf(defaultValue) }
    LaunchedEffect(key) {
        state.intValue = AppPreferencesDataStore.getInt(context, key, defaultValue)
    }
    return state
}

@Composable
fun rememberPrefFloat(key: String, defaultValue: Float): MutableFloatState {
    val context = LocalContext.current
    val state = remember { mutableFloatStateOf(defaultValue) }
    LaunchedEffect(key) {
        state.floatValue = AppPreferencesDataStore.getFloat(context, key, defaultValue)
    }
    return state
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun SliderDialog(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onDismiss: () -> Unit,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String = { "%.2f".format(it) }
) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                Text(
                    text = valueFormatter(sliderValue),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = valueRange,
                    steps = steps
                )
            }
        },
        confirmButton = {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clickableItem(
                    onClick = onDismiss,
                    label = cancelLabel,
                    weight = 1f,
                )
                clickableItem(
                    onClick = { onValueChange(sliderValue) },
                    label = confirmLabel,
                    weight = 1f,
                )
            }
        },
        dismissButton = {},
    )
}
