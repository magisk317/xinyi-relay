@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home
import io.github.magisk317.relay.ui.common.PrivacyPolicyDialog

import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.text.AppText

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import io.github.magisk317.uikit.common.AppSnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.metrics.performance.JankStats
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.smscode.runtime.common.utils.FrameworkCompatibilityMonitor
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.common.utils.relayPackageActions
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.AppUpdateDialog
import io.github.magisk317.uikit.theme.applyEdgeToEdge
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.update.FlavorPlayUpdateDelegate
import io.github.magisk317.uikit.update.UpdatePlayPolicy
import io.github.magisk317.uikit.update.WifiState
import io.github.magisk317.uikit.shell.PlayUpdateDelegate
import io.github.magisk317.relay.ui.nav.SmsCodeNavHost
import io.github.magisk317.relay.ui.privacy.PrivacyPolicyPage
import io.github.magisk317.relay.ui.theme.AppTheme
import io.github.magisk317.uikit.theme.ThemeRevealState
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import io.github.magisk317.relay.ui.home.settings.StartupPermissionPrompt
import io.github.magisk317.relay.ui.home.settings.SettingsViewModel
import io.github.magisk317.relay.ui.home.settings.SettingsEvent

class MainActivity : ComponentActivity() {

    private val playUpdateDelegate: PlayUpdateDelegate = FlavorPlayUpdateDelegate()
    private var autoUpdateChecked = false
    private var jankStats: JankStats? = null
    private val snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val newIntents = Channel<Intent>(capacity = Channel.BUFFERED)
    private val settingsRepository: SettingsPreferencesRepository by inject()

    private fun enqueueSnackbar(message: String) {
        snackbarMessages.tryEmit(message)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val incoming = Intent(intent)
        // Keep the latest Intent (including data) attached to the Activity until Compose has
        // handed it to the durable Settings queue. This remains a recovery source if enqueueing
        // into the transient Activity channel fails.
        setIntent(incoming)
        val enqueueResult = newIntents.trySend(incoming)
        if (enqueueResult.isFailure) {
            XLog.w(
                message = "Incoming intent channel rejected event: reason=%s",
                enqueueResult.exceptionOrNull()?.javaClass?.simpleName ?: "buffer_full",
            )
            lifecycleScope.launch {
                try {
                    newIntents.send(incoming)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    XLog.w(
                        message = "Incoming intent channel fallback failed: reason=%s",
                        failure.javaClass.simpleName,
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(this)
        playUpdateDelegate.onCreate(this) {
            relayPackageActions.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
        }
        triggerAutoUpdateIfEnabled()

        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            val languageState by viewModel.languageState.collectAsStateWithLifecycle()
            
            val context = LocalContext.current
            val locale = if (languageState.languageTag.isBlank()) {
                java.util.Locale.getDefault()
            } else {
                java.util.Locale.forLanguageTag(languageState.languageTag)
            }
            val currentConfiguration = LocalConfiguration.current
            val configuration = Configuration(currentConfiguration).apply {
                setLocale(locale)
            }
            val localizedContext = context.createConfigurationContext(configuration)
            
            fun getString(resId: Int): String = localizedContext.getString(resId)
            fun getString(resId: Int, vararg formatArgs: Any): String = localizedContext.getString(resId, *formatArgs)
            
            val navController = rememberNavController()
            val scope = rememberCoroutineScope()
            val appSnackbarHostState = remember { AppSnackbarHostState() }
            var mainContentBottomPadding by remember { mutableStateOf(0.dp) }
            val navigationBarPadding = WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
            val snackbarBottomPadding = maxOf(mainContentBottomPadding, navigationBarPadding)
            var privacyAccepted by remember { mutableStateOf<Boolean?>(null) }
            var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
            var showPrivacyPolicyPage by remember { mutableStateOf(false) }
            var blockingStartupDialog by remember { mutableStateOf<BlockingStartupDialog?>(null) }
            var startupCompatibilityChecked by remember { mutableStateOf(false) }

            // Circular Reveal Animation State
            var currentThemeMode by remember { mutableIntStateOf(themeState.mode) }
            var currentUiKitStyle by remember { mutableIntStateOf(themeState.uiKitStyle) }
            val themeRevealState = io.github.magisk317.uikit.theme.rememberThemeRevealState()
            val view = LocalView.current
            var requestedTab by remember { mutableStateOf<Any?>(null) }
            val launchIntent = remember { intent?.let(::Intent) }

            fun handleIncomingIntent(incoming: Intent?): Boolean {
                viewModel.handleArguments(incoming?.extras)
                val backupUri = incoming?.data ?: return true
                return if (viewModel.handleBackupArguments(backupUri)) {
                    requestedTab = io.github.magisk317.relay.ui.nav.SettingsRoute
                    true
                } else {
                    false
                }
            }

            fun clearHandledIntentData(incoming: Intent, allowEquivalentIntent: Boolean) {
                val handledData = incoming.data
                val currentIntent = this@MainActivity.intent
                if (handledData != null && currentIntent != null) {
                    val matchesHandledIntent = currentIntent === incoming ||
                        (allowEquivalentIntent && currentIntent.data == handledData)
                    if (matchesHandledIntent) {
                        // Mutate the current object as well as setting it back on the Activity.
                        // Keeping the same object helps relaunches observe consumed data.
                        currentIntent.data = null
                        setIntent(currentIntent)
                    }
                }
            }

            LaunchedEffect(Unit) {
                val accepted = settingsRepository.isPrivacyPolicyAccepted()
                privacyAccepted = accepted
                if (!accepted) {
                    showPrivacyPolicyDialog = true
                }
            }
            LaunchedEffect(Unit) {
                try {
                    if (BuildConfig.ALLOW_CONFLICT_BYPASS) {
                        XLog.w(
                            "SmsCode conflict guard bypassed by build flag allowConflictBypass=true",
                        )
                    } else if (relayPackageActions.isPackageInstalled(context, Const.XPOSED_SMSCODE_PACKAGE_NAME)) {
                        blockingStartupDialog = BlockingStartupDialog.SmsCodeConflict
                        return@LaunchedEffect
                    }
                    val frameworkIssue = withContext(Dispatchers.IO) {
                        relayPackageActions.inspectFrameworkIssue(context)
                    }
                    if (frameworkIssue != null) {
                        blockingStartupDialog = BlockingStartupDialog.FrameworkIncompatibility(frameworkIssue)
                    }
                } finally {
                    startupCompatibilityChecked = true
                }
            }
            LaunchedEffect(Unit) {
                snackbarMessages.collect { message ->
                    appSnackbarHostState.showLatestSnackbar(message)
                }
            }

            // Effect to trigger logic when ThemeState changes
            LaunchedEffect(themeState) {
                applyThemeReveal(
                    themeState = themeState,
                    currentThemeMode = currentThemeMode,
                    currentUiKitStyle = currentUiKitStyle,
                    view = view,
                    themeRevealState = themeRevealState,
                    onThemeApplied = { mode, style ->
                        currentThemeMode = mode
                        currentUiKitStyle = style
                    },
                )
            }

            // Collect navigation events
            LaunchedEffect(viewModel.eventsFlow) {
                viewModel.eventsFlow.collect { event ->
                    when (event) {
                        is SettingsEvent.ShowPrivacyPolicy -> showPrivacyPolicyDialog = true
                        is SettingsEvent.NavigateToRules -> {
                            requestedTab = io.github.magisk317.relay.ui.nav.SmsCodeRulesRoute()
                        }
                        is SettingsEvent.NavigateToRecords -> requestedTab = io.github.magisk317.relay.ui.nav.RecordsRoute
                        is SettingsEvent.StartPlayUpdate -> requestPlayUpdate()
                        is SettingsEvent.ShowSnackbar -> {
                            scope.launch { appSnackbarHostState.showLatestSnackbar(event.message) }
                        }
                    }
                }
            }

            val activity = context as ComponentActivity
            CompositionLocalProvider(
                LocalSnackbarHostState provides appSnackbarHostState,
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                androidx.activity.compose.LocalActivity provides activity,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides activity,
                androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides activity,
            ) {
                AppTheme(
                    themeMode = currentThemeMode,
                    uiKitStyle = currentUiKitStyle,
                    layoutScale = themeState.layoutScale,
                    paletteStyle = themeState.paletteStyle,
                    colorSpec = themeState.colorSpec,
                    monetEnabled = themeState.monetEnabled,
                    surfaceBlur = themeState.surfaceBlur,
                    dynamicColor = themeState.dynamicColor,
                    accentColor = themeState.accentColor,
                ) {
                    AppSurface(color = appColor(AppColorRole.Background)) {
                        LaunchedEffect(Unit) {
                            viewModel.setInternalFilesWritable()
                        }
                        LaunchedEffect(viewModel, launchIntent) {
                            if (launchIntent != null && handleIncomingIntent(launchIntent)) {
                                clearHandledIntentData(
                                    incoming = launchIntent,
                                    allowEquivalentIntent = true,
                                )
                            }
                        }
                        LaunchedEffect(viewModel) {
                            newIntents.receiveAsFlow().collect { incoming ->
                                if (handleIncomingIntent(incoming)) {
                                    clearHandledIntentData(
                                        incoming = incoming,
                                        allowEquivalentIntent = false,
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            SmsCodeNavHost(
                                navController = navController,
                                onBack = { finish() },
                                initialTab = requestedTab,
                                onInitialTabConsumed = { requestedTab = null },
                                onBottomContentPaddingChanged = { mainContentBottomPadding = it },
                                modifier = Modifier,
                            )

                            if (blockingStartupDialog == null &&
                                privacyAccepted == true &&
                                !showPrivacyPolicyDialog &&
                                !showPrivacyPolicyPage
                            ) {
                                val updateController = koinInject<AppUpdateController>()
                                LaunchedEffect(Unit) {
                                    // 冷启动静默检测更新：后台不打扰用户，强制更新照常弹窗
                                    updateController.check(userInitiated = false)
                                }
                                AppUpdateDialog(
                                    controller = updateController,
                                    onInstall = { updateController.install(this@MainActivity) },
                                )
                            }

                        StartupPermissionPrompt(
                            enabled = startupCompatibilityChecked &&
                                blockingStartupDialog == null &&
                                privacyAccepted == true &&
                                !showPrivacyPolicyDialog &&
                                !showPrivacyPolicyPage,
                        )

                        if (blockingStartupDialog == null && showPrivacyPolicyDialog) {
                            PrivacyPolicyDialog(
                                onDismiss = {},
                                onConfirm = {
                                    scope.launch { settingsRepository.setPrivacyPolicyAccepted(true) }
                                    privacyAccepted = true
                                    showPrivacyPolicyDialog = false
                                },
                                onCancel = {
                                    scope.launch { settingsRepository.setPrivacyPolicyAccepted(false) }
                                    privacyAccepted = false
                                    showPrivacyPolicyDialog = false
                                    finish()
                                },
                                onViewPolicy = {
                                    showPrivacyPolicyDialog = false
                                    showPrivacyPolicyPage = true
                                },
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                            )
                        }

                        if (blockingStartupDialog == null && showPrivacyPolicyPage) {
                            PrivacyPolicyPage(
                                onDismiss = {
                                    showPrivacyPolicyPage = false
                                    scope.launch {
                                        if (!settingsRepository.isPrivacyPolicyAccepted()) {
                                            showPrivacyPolicyDialog = true
                                        }
                                    }
                                },
                            )
                        }

                        blockingStartupDialog?.let { dialog ->
                            ExitOnlyConflictDialog(
                                title = when (dialog) {
                                    is BlockingStartupDialog.SmsCodeConflict ->
                                        getString(R.string.smscode_conflict_dialog_title)
                                    is BlockingStartupDialog.FrameworkIncompatibility ->
                                        getString(R.string.framework_incompatibility_title)
                                },
                                text = when (dialog) {
                                    is BlockingStartupDialog.SmsCodeConflict -> {
                                        buildAnnotatedString {
                                            append(getString(R.string.smscode_conflict_dialog_prefix))
                                            withStyle(
                                                SpanStyle(
                                                    color = appColor(AppColorRole.Error),
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            ) {
                                                append(getString(R.string.smscode_conflict_other_app_name))
                                            }
                                            append(" (")
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append(Const.XPOSED_SMSCODE_PACKAGE_NAME)
                                            }
                                            append(")")
                                            append(getString(R.string.smscode_conflict_dialog_middle))
                                            withStyle(
                                                SpanStyle(
                                                    color = appColor(AppColorRole.Error),
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            ) {
                                                append(getString(R.string.app_name))
                                            }
                                            append(getString(R.string.smscode_conflict_dialog_suffix))
                                        }
                                    }
                                    is BlockingStartupDialog.FrameworkIncompatibility -> {
                                        val issue = dialog.issue
                                        buildAnnotatedString {
                                            append(
                                                when (issue.issueType) {
                                                    FrameworkCompatibilityMonitor.FrameworkIssueType.HOOKER_ANNOTATION_INCOMPATIBLE ->
                                                        getString(R.string.framework_incompatibility_hooker_annotation_message)
                                                },
                                            )
                                        }
                                    }
                                },
                                confirmText = getString(R.string.smscode_conflict_dialog_exit),
                                onExit = {
                                    blockingStartupDialog = null
                                    finish()
                                },
                            )
                        }

                        io.github.magisk317.uikit.theme.ThemeRevealOverlay(themeRevealState)
                            io.github.magisk317.uikit.common.AppSnackbarHost(
                                hostState = appSnackbarHostState,
                                modifier = Modifier
                                    .padding(bottom = snackbarBottomPadding),
                            )
                        }
                    }
                }
            }
        }
        installJankStatsIfDebug()
    }

    private fun installJankStatsIfDebug() {
        if (!BuildConfig.DEBUG || jankStats != null) return
        val decorView = window.peekDecorView()
        if (decorView == null) {
            window.decorView.post { installJankStatsIfDebug() }
            return
        }
        jankStats = JankStats.createAndTrack(window) { frameData ->
            if (frameData.isJank) {
                XLog.d(message = "UI jank frame: %s", frameData)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        jankStats?.isTrackingEnabled = true
        playUpdateDelegate.onResume(this) {
            relayPackageActions.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
        }
    }

    override fun onPause() {
        jankStats?.isTrackingEnabled = false
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playUpdateDelegate.onDestroy()
    }

    private fun requestPlayUpdate() {
        requestPlayUpdateInternal(silentIfNoUpdate = false, fallbackOnQueryFailure = true)
    }

    private fun requestPlayUpdateInternal(silentIfNoUpdate: Boolean, fallbackOnQueryFailure: Boolean) {
        playUpdateDelegate.requestUpdate(
            activity = this,
            silentIfNoUpdate = silentIfNoUpdate,
            fallbackOnQueryFailure = fallbackOnQueryFailure,
        ) {
            relayPackageActions.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
        }
    }

    private fun triggerAutoUpdateIfEnabled() {
        if (!BuildConfig.HAS_BILLING || autoUpdateChecked) return
        autoUpdateChecked = true

        lifecycleScope.launch {
            val updateSettings = settingsRepository.getAutoUpdateSettings()
            val enabled = updateSettings.enabled
            if (!enabled) return@launch
            val wifiOnly = updateSettings.wifiOnly
            val onWifi = WifiState.isOnWifi(this@MainActivity)
            if (!UpdatePlayPolicy.shouldRunAutoCheck(enabled, wifiOnly, onWifi)) return@launch

            requestPlayUpdateInternal(silentIfNoUpdate = true, fallbackOnQueryFailure = false)
        }
    }

    /**
     * Runs the circular-reveal animation when the theme mode or the UI-kit style changes,
     * and applies the new values directly when no animation is needed (initial load) or the
     * reveal is skipped because the view cannot be captured.
     *
     * Extracted from onCreate so the entry point stays readable; the reveal decision tree
     * (mode change vs. style swap vs. initial load) lives here under a name.
     */
    private suspend fun applyThemeReveal(
        themeState: SettingsViewModel.ThemeState,
        currentThemeMode: Int,
        currentUiKitStyle: Int,
        view: View,
        themeRevealState: ThemeRevealState,
        onThemeApplied: (mode: Int, style: Int) -> Unit,
    ) {
        if (themeState.mode != currentThemeMode) {
            val requestedCenter = if (themeState.centerX >= 0f && themeState.centerY >= 0f) {
                Offset(themeState.centerX, themeState.centerY)
            } else {
                Offset.Unspecified
            }
            val animated = themeRevealState.animateThemeChange(
                view = view,
                requestedCenter = requestedCenter,
            ) {
                onThemeApplied(themeState.mode, themeState.uiKitStyle)
            }
            if (!animated) {
                onThemeApplied(themeState.mode, themeState.uiKitStyle)
            }
        } else if (themeState.uiKitStyle != currentUiKitStyle) {
            // A UI-kit style swap replaces the whole UI tree, so it deserves the
            // same reveal treatment as a theme-mode change. No tap origin is
            // attached to a style change, so the reveal grows from the center.
            val animated = themeRevealState.animateThemeChange(view = view) {
                onThemeApplied(currentThemeMode, themeState.uiKitStyle)
            }
            if (!animated) {
                onThemeApplied(currentThemeMode, themeState.uiKitStyle)
            }
        } else {
            // Initial load
            onThemeApplied(themeState.mode, themeState.uiKitStyle)
        }
    }

}

private sealed interface BlockingStartupDialog {
    data object SmsCodeConflict : BlockingStartupDialog
    data class FrameworkIncompatibility(
        val issue: FrameworkCompatibilityMonitor.FrameworkIssue,
    ) : BlockingStartupDialog
}

@Composable
private fun ExitOnlyConflictDialog(
    title: String,
    text: androidx.compose.ui.text.AnnotatedString,
    confirmText: String,
    onExit: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = {},
        title = { AppText(text = title) },
        text = { AppText(text = text) },
        confirmButton = {
            AppPrimaryButton(text = confirmText, onClick = onExit)
        },
    )
}
