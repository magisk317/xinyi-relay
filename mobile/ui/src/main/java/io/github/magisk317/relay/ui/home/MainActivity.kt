@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home
import io.github.magisk317.relay.ui.common.PrivacyPolicyDialog

import io.github.magisk317.uikit.common.showLatestSnackbar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.smscode.runtime.common.utils.FrameworkCompatibilityMonitor
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.common.utils.PackageUtils
import io.github.magisk317.smscode.runtime.common.utils.BrowserUtils
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.smscode.runtime.common.update.ApkSecurityVerifier
import io.github.magisk317.relay.update.GithubUpdateChecker
import io.github.magisk317.smscode.runtime.contract.update.UpgradeApkAsset
import io.github.magisk317.relay.update.UpgradeDownloader
import io.github.magisk317.relay.update.UpgradeInstaller
import io.github.magisk317.smscode.runtime.contract.update.GithubReleaseInfo
import io.github.magisk317.smscode.runtime.contract.update.UpgradeCheckResult
import io.github.magisk317.smscode.runtime.contract.update.UpgradeInfo
import io.github.magisk317.smscode.runtime.common.update.UpdatePolicy
import io.github.magisk317.uikit.theme.UpdateSystemBars
import io.github.magisk317.uikit.theme.applyEdgeToEdge
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import io.github.magisk317.relay.ui.home.update.FlavorPlayUpdateDelegate
import io.github.magisk317.relay.ui.home.update.PlayUpdateDelegate
import io.github.magisk317.relay.ui.nav.SmsCodeNavHost
import io.github.magisk317.relay.ui.privacy.PrivacyPolicyPage
import io.github.magisk317.relay.ui.theme.AppTheme
import io.github.magisk317.uikit.theme.UiKitStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import java.io.File
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
                "Incoming intent channel rejected event: reason=%s",
                enqueueResult.exceptionOrNull()?.javaClass?.simpleName ?: "buffer_full",
            )
            lifecycleScope.launch {
                try {
                    newIntents.send(incoming)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    XLog.w(
                        "Incoming intent channel fallback failed: reason=%s",
                        failure.javaClass.simpleName,
                    )
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(this)
        playUpdateDelegate.onCreate(this) {
            PackageUtils.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
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
            val appSnackbarHostState = remember { SnackbarHostState() }
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
            var githubUpdateUiState by remember { mutableStateOf<GithubUpdateUiState?>(null) }
            var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
            var unknownSourceApk by remember { mutableStateOf<File?>(null) }
            var downloadJob by remember { mutableStateOf<Job?>(null) }

            fun startStructuredDownload(update: GithubStructuredUpdate) {
                downloadJob?.cancel()
                downloadState = UpdateDownloadState.Downloading(progress = 0f, progressText = "0%")
                downloadJob = scope.launch {
                    try {
                        val downloadedFile = UpgradeDownloader.download(
                            context = this@MainActivity,
                            versionCode = update.info.versionCode,
                            asset = update.asset,
                        ) { progress ->
                            runOnUiThread {
                                downloadState = UpdateDownloadState.Downloading(
                                    progress = progress.percent,
                                    progressText = formatDownloadProgress(progress),
                                )
                            }
                        }
                        val verifyResult = ApkSecurityVerifier.verifyDownloadedApk(
                            context = this@MainActivity,
                            apkFile = downloadedFile,
                            expectedSha256 = update.asset.sha256,
                            expectedSigningCertSha256 = update.info.signingCertSha256,
                        )
                        if (!verifyResult.success) {
                            runCatching { downloadedFile.delete() }
                            downloadState = UpdateDownloadState.Failed(
                                message = getString(
                                    R.string.update_security_check_failed,
                                    verifyResult.reason ?: "unknown",
                                ),
                                retry = update,
                            )
                            return@launch
                        }
                        downloadState = UpdateDownloadState.Downloaded(
                            file = downloadedFile,
                            update = update,
                        )
                    } catch (_: CancellationException) {
                        downloadState = UpdateDownloadState.Idle
                    } catch (t: Throwable) {
                        downloadState = UpdateDownloadState.Failed(
                            message = t.message ?: t.javaClass.simpleName,
                            retry = update,
                        )
                    } finally {
                        downloadJob = null
                    }
                }
            }

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
                    } else if (PackageUtils.isPackageInstalled(context, Const.XPOSED_SMSCODE_PACKAGE_NAME)) {
                        blockingStartupDialog = BlockingStartupDialog.SmsCodeConflict
                        return@LaunchedEffect
                    }
                    val frameworkIssue = withContext(Dispatchers.IO) {
                        PackageUtils.inspectFrameworkIssue(context)
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
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                    }
                    if (!animated) {
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                    }
                } else if (themeState.uiKitStyle != currentUiKitStyle) {
                    currentUiKitStyle = themeState.uiKitStyle
                } else {
                    // Initial load
                    currentThemeMode = themeState.mode
                    currentUiKitStyle = themeState.uiKitStyle
                }
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
                AppTheme(themeMode = currentThemeMode, uiKitStyle = currentUiKitStyle) {
                    Surface(color = MaterialTheme.colorScheme.background) {
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
                                                    color = MaterialTheme.colorScheme.error,
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
                                                    color = MaterialTheme.colorScheme.error,
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

                        if (blockingStartupDialog == null) {
                            githubUpdateUiState?.let { updateState ->
                            AlertDialog(
                                onDismissRequest = { githubUpdateUiState = null },
                                title = { Text(getString(R.string.github_update_dialog_title)) },
                                text = {
                                    Text(
                                        text = buildUpdateDialogText(updateState),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 400.dp)
                                            .verticalScroll(rememberScrollState()),
                                    )
                                },
                                confirmButton = {
                                    FilledTonalButton(
                                        onClick = {
                                            when (updateState) {
                                                is GithubUpdateUiState.ReleaseLink -> {
                                                    BrowserUtils.openWebPage(
                                                        this@MainActivity,
                                                        updateState.release.htmlUrl,
                                                        R.string.browser_install_or_enable_prompt,
                                                    )
                                                        ?.let(::enqueueSnackbar)
                                                    githubUpdateUiState = null
                                                }

                                                is GithubUpdateUiState.Structured -> {
                                                    githubUpdateUiState = null
                                                    startStructuredDownload(updateState.update)
                                                }
                                            }
                                        },
                                    ) {
                                        Text(getString(R.string.github_update_download))
                                    }
                                },
                                dismissButton = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val versionName = when (updateState) {
                                                    is GithubUpdateUiState.ReleaseLink -> updateState.release.versionName
                                                    is GithubUpdateUiState.Structured -> updateState.update.info.versionName
                                                }
                                                lifecycleScope.launch {
                                                    settingsRepository.setIgnoredGithubVersion(versionName)
                                                }
                                                githubUpdateUiState = null
                                            },
                                        ) {
                                            Text(getString(R.string.github_update_ignore_this_version))
                                        }
                                        OutlinedButton(onClick = { githubUpdateUiState = null }) {
                                            Text(getString(R.string.cancel))
                                        }
                                    }
                                },
                            )
                        }
                        }

                        when (val state = downloadState) {
                            is UpdateDownloadState.Downloading -> {
                                AlertDialog(
                                    onDismissRequest = {},
                                    title = { Text(getString(R.string.update_download_in_progress_title)) },
                                    text = {
                                        Column {
                                            LinearProgressIndicator(
                                                progress = { state.progress },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(state.progressText)
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                downloadJob?.cancel()
                                                downloadState = UpdateDownloadState.Idle
                                            },
                                        ) {
                                            Text(getString(R.string.update_download_cancel))
                                        }
                                    },
                                )
                            }

                            is UpdateDownloadState.Failed -> {
                                AlertDialog(
                                    onDismissRequest = { downloadState = UpdateDownloadState.Idle },
                                    title = { Text(getString(R.string.update_download_failed_title)) },
                                    text = { Text(state.message) },
                                    dismissButton = {
                                        OutlinedButton(onClick = { downloadState = UpdateDownloadState.Idle }) {
                                            Text(getString(R.string.cancel))
                                        }
                                    },
                                    confirmButton = {
                                        if (state.retry != null) {
                                            FilledTonalButton(onClick = { startStructuredDownload(state.retry) }) {
                                                Text(getString(R.string.update_retry))
                                            }
                                        }
                                    },
                                )
                            }

                            is UpdateDownloadState.Downloaded -> {
                                AlertDialog(
                                    onDismissRequest = {},
                                    title = { Text(getString(R.string.update_download_completed_title)) },
                                    text = { Text(getString(R.string.update_download_completed_message)) },
                                    dismissButton = {
                                        OutlinedButton(onClick = { downloadState = UpdateDownloadState.Idle }) {
                                            Text(getString(R.string.cancel))
                                        }
                                    },
                                    confirmButton = {
                                        FilledTonalButton(
                                            onClick = {
                                                if (!UpgradeInstaller.canRequestPackageInstalls(this@MainActivity)) {
                                                    unknownSourceApk = state.file
                                                    return@FilledTonalButton
                                                }
                                                val installResult = UpgradeInstaller.installApk(this@MainActivity, state.file)
                                                if (installResult.isSuccess) {
                                                    downloadState = UpdateDownloadState.Idle
                                                } else {
                                                    downloadState = UpdateDownloadState.Failed(
                                                        message = installResult.exceptionOrNull()?.message
                                                            ?: "install_failed",
                                                        retry = state.update,
                                                    )
                                                }
                                            },
                                        ) {
                                            Text(getString(R.string.update_install))
                                        }
                                    },
                                )
                            }

                            UpdateDownloadState.Idle -> Unit
                        }

                        unknownSourceApk?.let {
                            AlertDialog(
                                onDismissRequest = { unknownSourceApk = null },
                                title = { Text(getString(R.string.update_unknown_source_title)) },
                                text = { Text(getString(R.string.update_unknown_source_message)) },
                                dismissButton = {
                                    OutlinedButton(onClick = { unknownSourceApk = null }) {
                                        Text(getString(R.string.cancel))
                                    }
                                },
                                confirmButton = {
                                    FilledTonalButton(
                                        onClick = {
                                            startActivity(UpgradeInstaller.buildUnknownSourceSettingsIntent(this@MainActivity))
                                            unknownSourceApk = null
                                        },
                                    ) {
                                        Text(getString(R.string.update_open_settings))
                                    }
                                },
                            )
                        }

                        io.github.magisk317.uikit.theme.ThemeRevealOverlay(themeRevealState)
                            io.github.magisk317.uikit.common.DismissibleSnackbarHost(
                                hostState = appSnackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
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
                XLog.d("UI jank frame: %s", frameData)
            }
        }
    }



    override fun onResume() {
        super.onResume()
        jankStats?.isTrackingEnabled = true
        playUpdateDelegate.onResume(this) {
            PackageUtils.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
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
            PackageUtils.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
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
            val onWifi = PackageUtils.isOnWifi(this@MainActivity)
            if (!UpdatePolicy.shouldRunAutoCheck(enabled, wifiOnly, onWifi)) return@launch

            requestPlayUpdateInternal(silentIfNoUpdate = true, fallbackOnQueryFailure = false)
        }
    }

    private suspend fun checkStartupGithubUpdateIfNeeded(): GithubUpdateUiState? {
        if (!autoUpdateChecked) triggerAutoUpdateIfEnabled()
        val result = findGithubUpdate(
            isAutoCheck = true,
            respectIgnoredVersion = true,
        )
        return when (result) {
            is GithubUpdateQueryResult.Available -> result.update
            else -> null
        }
    }

    private fun requestGithubUpdateCheck(
        showNoUpdateMessage: Boolean,
        onUpdateFound: (GithubUpdateUiState) -> Unit,
    ) {
        lifecycleScope.launch {
            when (
                val result = findGithubUpdate(
                    isAutoCheck = false,
                    respectIgnoredVersion = false,
                )
            ) {
                is GithubUpdateQueryResult.Failed -> {
                    enqueueSnackbar(getString(R.string.check_update_failed))
                }

                GithubUpdateQueryResult.NoUpdate -> {
                    if (showNoUpdateMessage) {
                        enqueueSnackbar(getString(R.string.app_already_newest))
                    }
                }

                is GithubUpdateQueryResult.Available -> {
                    onUpdateFound(result.update)
                }
            }
        }
    }

    private suspend fun findGithubUpdate(
        isAutoCheck: Boolean,
        respectIgnoredVersion: Boolean,
    ): GithubUpdateQueryResult {
        val installedFromPlay = PackageUtils.isInstalledFromPlay(this)
        val updateSettings = settingsRepository.getAutoUpdateSettings()
        if (isAutoCheck) {
            val enabled = updateSettings.enabled
            if (!enabled) return GithubUpdateQueryResult.NoUpdate

            val wifiOnly = updateSettings.wifiOnly
            val onWifi = PackageUtils.isOnWifi(this)
            if (UpdatePolicy.shouldSkipGithubCheckOnStartup(installedFromPlay, enabled, wifiOnly, onWifi)) {
                return GithubUpdateQueryResult.NoUpdate
            }
        } else if (installedFromPlay) {
            return GithubUpdateQueryResult.NoUpdate
        }

        val checkResult = GithubUpdateChecker.fetchUpgradeInfo()
        val updateState = when (checkResult) {
            is UpgradeCheckResult.CheckFailed -> {
                return if (isAutoCheck) {
                    GithubUpdateQueryResult.NoUpdate
                } else {
                    GithubUpdateQueryResult.Failed(checkResult.message)
                }
            }

            UpgradeCheckResult.NoUpdate -> return GithubUpdateQueryResult.NoUpdate
            is UpgradeCheckResult.ReleaseLink -> {
                if (!GithubUpdateChecker.isNewer(BuildConfig.VERSION_NAME, checkResult.release.versionName)) {
                    return GithubUpdateQueryResult.NoUpdate
                }
                GithubUpdateUiState.ReleaseLink(checkResult.release)
            }

            is UpgradeCheckResult.Structured -> {
                val info = checkResult.info
                val newer = if (info.versionCode > 0L) {
                    GithubUpdateChecker.isNewer(BuildConfig.VERSION_CODE.toLong(), info.versionCode)
                } else {
                    GithubUpdateChecker.isNewer(BuildConfig.VERSION_NAME, info.versionName)
                }
                if (!newer) {
                    return GithubUpdateQueryResult.NoUpdate
                }

                val selectedApk = GithubUpdateChecker.selectBestApkForDevice(
                    apks = info.apks,
                )
                if (selectedApk != null && selectedApk.sha256.isNotBlank() && info.signingCertSha256.isNotBlank()) {
                    GithubUpdateUiState.Structured(
                        update = GithubStructuredUpdate(
                            info = info,
                            asset = selectedApk,
                        ),
                    )
                } else {
                    GithubUpdateUiState.ReleaseLink(
                        GithubReleaseInfo(
                            versionName = info.versionName,
                            htmlUrl = info.htmlUrl.ifBlank { Const.PROJECT_GITHUB_LATEST_RELEASE_URL },
                        ),
                    )
                }
            }
        }

        if (respectIgnoredVersion) {
            val ignoredVersion = updateSettings.ignoredGithubVersion
            val latestVersionName = when (updateState) {
                is GithubUpdateUiState.ReleaseLink -> updateState.release.versionName
                is GithubUpdateUiState.Structured -> updateState.update.info.versionName
            }
            if (UpdatePolicy.shouldSkipIgnoredVersion(respectIgnoredVersion, ignoredVersion, latestVersionName)) {
                return GithubUpdateQueryResult.NoUpdate
            }
        }
        return GithubUpdateQueryResult.Available(updateState)
    }

    private fun buildUpdateDialogText(updateState: GithubUpdateUiState): String {
        return when (updateState) {
            is GithubUpdateUiState.ReleaseLink -> {
                getString(R.string.github_update_dialog_message, updateState.release.versionName)
            }

            is GithubUpdateUiState.Structured -> {
                val info = updateState.update.info
                val title = "v${BuildConfig.VERSION_NAME} -> v${info.versionName}"
                val matchedLogs = info.versionLogs.filter { it.code > BuildConfig.VERSION_CODE.toLong() }
                val content = when {
                    matchedLogs.size > 1 -> matchedLogs.joinToString("\n\n") { log ->
                        "v${log.name}\n${log.desc}"
                    }
                    matchedLogs.isNotEmpty() -> matchedLogs.first().desc
                    info.changelog.isNotBlank() -> info.changelog
                    else -> getString(R.string.github_update_dialog_message, info.versionName)
                }
                "$title\n\n$content".trim()
            }
        }
    }

    private fun formatDownloadProgress(progress: UpgradeDownloader.Progress): String {
        val percent = (progress.percent * 100f).toInt().coerceIn(0, 100)
        val current = formatBytes(progress.bytesRead)
        val total = if (progress.totalBytes > 0L) formatBytes(progress.totalBytes) else "?"
        return getString(R.string.update_download_progress_text, percent, current, total)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= BYTES_PER_UNIT && index < units.lastIndex) {
            value /= BYTES_PER_UNIT
            index++
        }
        return if (index == 0) {
            "${value.toInt()}${units[index]}"
        } else {
            String.format("%.1f%s", value, units[index])
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
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            FilledTonalButton(onClick = onExit) {
                Text(confirmText)
            }
        },
    )
}

private data class GithubStructuredUpdate(
    val info: UpgradeInfo,
    val asset: UpgradeApkAsset,
)

private sealed class GithubUpdateUiState {
    data class ReleaseLink(val release: GithubReleaseInfo) : GithubUpdateUiState()
    data class Structured(val update: GithubStructuredUpdate) : GithubUpdateUiState()
}

private sealed class GithubUpdateQueryResult {
    data object NoUpdate : GithubUpdateQueryResult()
    data class Failed(val message: String?) : GithubUpdateQueryResult()
    data class Available(val update: GithubUpdateUiState) : GithubUpdateQueryResult()
}

private sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val progressText: String) : UpdateDownloadState()
    data class Failed(val message: String, val retry: GithubStructuredUpdate?) : UpdateDownloadState()
    data class Downloaded(val file: File, val update: GithubStructuredUpdate) : UpdateDownloadState()
}

private const val BYTES_PER_UNIT = 1024.0
private const val MAX_CAPTURE_PIXELS = 8_388_608L
private const val MAX_CAPTURE_SIDE_PX = 4096
private const val LARGE_BITMAP_ERROR_KEYWORD = "trying to draw too large"
