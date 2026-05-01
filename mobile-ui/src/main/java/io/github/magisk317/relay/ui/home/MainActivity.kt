@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.smscode.runtime.common.utils.FrameworkCompatibilityMonitor
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.common.utils.PackageUtils
import io.github.magisk317.relay.common.utils.Utils
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.data.update.ApkSecurityVerifier
import io.github.magisk317.relay.data.update.GithubUpdateChecker
import io.github.magisk317.smscode.runtime.common.update.UpgradeApkAsset
import io.github.magisk317.relay.data.update.UpgradeDownloader
import io.github.magisk317.relay.data.update.UpgradeInstaller
import io.github.magisk317.smscode.runtime.common.update.GithubReleaseInfo
import io.github.magisk317.smscode.runtime.common.update.UpgradeCheckResult
import io.github.magisk317.smscode.runtime.common.update.UpgradeInfo
import io.github.magisk317.smscode.runtime.common.update.UpdatePolicy
import io.github.magisk317.relay.ui.app.base.UpdateSystemBars
import io.github.magisk317.relay.ui.app.base.applyEdgeToEdge
import io.github.magisk317.relay.ui.app.base.rememberHazeStyle
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import io.github.magisk317.relay.ui.home.update.FlavorPlayUpdateDelegate
import io.github.magisk317.relay.ui.home.update.PlayUpdateDelegate
import io.github.magisk317.relay.ui.nav.SmsCodeNavHost
import io.github.magisk317.relay.ui.privacy.PrivacyPolicyPage
import io.github.magisk317.relay.ui.theme.AppTheme
import io.github.magisk317.uikit.theme.UiKitStyle
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.androidx.compose.koinViewModel
import java.io.File
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private val playUpdateDelegate: PlayUpdateDelegate = FlavorPlayUpdateDelegate()
    private var autoUpdateChecked = false
    private val snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val settingsRepository: SettingsRepository by lazy {
        RuntimeGraph.from(applicationContext).settingsRepository
    }

    private fun enqueueSnackbar(message: String) {
        snackbarMessages.tryEmit(message)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        applyStoredLanguage()
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(this)
        playUpdateDelegate.onCreate(this) {
            PackageUtils.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
        }
        triggerAutoUpdateIfEnabled()

        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            val navController = rememberNavController()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val appSnackbarHostState = remember { SnackbarHostState() }
            var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
            var showPrivacyPolicyPage by remember { mutableStateOf(false) }
            var blockingStartupDialog by remember { mutableStateOf<BlockingStartupDialog?>(null) }
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
            var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val revealAnim = remember { Animatable(0f) }
            var isAnimating by remember { mutableStateOf(false) }
            var animationCenter by remember { mutableStateOf(Offset.Zero) }
            val view = LocalView.current
            var requestedTab by remember { mutableStateOf<Any?>(null) }

            fun clearScreenshotBitmap() {
                screenshotBitmap?.let { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
                screenshotBitmap = null
            }

            LaunchedEffect(Unit) {
                if (!settingsRepository.isPrivacyPolicyAccepted()) {
                    showPrivacyPolicyDialog = true
                }
            }
            LaunchedEffect(Unit) {
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
            }
            LaunchedEffect(Unit) {
                githubUpdateUiState = checkStartupGithubUpdateIfNeeded()
            }
            LaunchedEffect(Unit) {
                snackbarMessages.collect { message ->
                    appSnackbarHostState.showSnackbar(message)
                }
            }

            // Effect to trigger logic when ThemeState changes
            LaunchedEffect(themeState) {
                if (themeState.mode != currentThemeMode) {
                    val width = view.width
                    val height = view.height
                    val pixelCount = width.toLong() * height.toLong()
                    val exceedsLimits = width <= 0 ||
                        height <= 0 ||
                        width > MAX_CAPTURE_SIDE_PX ||
                        height > MAX_CAPTURE_SIDE_PX ||
                        pixelCount > MAX_CAPTURE_PIXELS
                    if (exceedsLimits) {
                        XLog.w(
                            "Skip theme capture due to size: width=%d height=%d pixels=%d",
                            width,
                            height,
                            pixelCount,
                        )
                        clearScreenshotBitmap()
                        isAnimating = false
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                        return@LaunchedEffect
                    }

                    try {
                        clearScreenshotBitmap()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        view.draw(canvas)
                        screenshotBitmap = bitmap

                        val centerX = if (themeState.centerX >= 0) themeState.centerX else width / 2f
                        val centerY = if (themeState.centerY >= 0) themeState.centerY else height / 2f
                        animationCenter = Offset(centerX, centerY)

                        isAnimating = true
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle

                        revealAnim.snapTo(0f)
                        revealAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 600),
                        )
                    } catch (oom: OutOfMemoryError) {
                        XLog.w("Theme capture OOM, fallback to direct mode switch", oom)
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                    } catch (e: RuntimeException) {
                        if (e.message?.contains(LARGE_BITMAP_ERROR_KEYWORD, ignoreCase = true) == true) {
                            XLog.w("Theme capture too large bitmap, fallback to direct mode switch")
                        } else {
                            XLog.w("Theme capture runtime exception: %s", e.message ?: "unknown")
                        }
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                    } catch (t: Throwable) {
                        XLog.w("Theme capture failed: %s", t.message ?: "unknown")
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                    } finally {
                        isAnimating = false
                        clearScreenshotBitmap()
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
                        is SettingsEvent.SmsCodeTestResult -> {
                            val message = if (event.code.isBlank()) {
                                context.getString(R.string.cannot_parse_relay_code)
                            } else {
                                val base = context.getString(R.string.current_sms_code, event.code)
                                val hitRule = event.matchedRuleLabel?.takeIf { it.isNotBlank() }?.let {
                                    context.getString(R.string.hit_rule_label, it)
                                }
                                if (hitRule == null) {
                                    base
                                } else {
                                    context.getString(R.string.sms_code_test_result_with_rule, base, hitRule)
                                }
                            }
                            scope.launch { appSnackbarHostState.showSnackbar(message) }
                        }
                        is SettingsEvent.NavigateToRules -> {
                            requestedTab = io.github.magisk317.relay.ui.nav.SmsCodeRulesRoute()
                        }
                        is SettingsEvent.NavigateToRecords -> requestedTab = io.github.magisk317.relay.ui.nav.RecordsRoute
                        is SettingsEvent.StartPlayUpdate -> requestPlayUpdate()
                        is SettingsEvent.StartGithubUpdateCheck -> {
                            requestGithubUpdateCheck(showNoUpdateMessage = true) { update ->
                                githubUpdateUiState = update
                            }
                        }
                        is SettingsEvent.ShowSnackbar -> {
                            scope.launch { appSnackbarHostState.showSnackbar(event.message) }
                        }
                        else -> {}
                    }
                }
            }

            CompositionLocalProvider(LocalSnackbarHostState provides appSnackbarHostState) {
                AppTheme(themeMode = currentThemeMode, uiKitStyle = currentUiKitStyle) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        LaunchedEffect(Unit) {
                            viewModel.setInternalFilesWritable()
                        }
                        LaunchedEffect(intent) {
                            viewModel.handleArguments(intent.extras)
                            if (intent?.data != null) {
                                requestedTab = io.github.magisk317.relay.ui.nav.SettingsRoute
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            val hazeBlurRadius by settingsRepository.getHazeBlurRadiusFlow()
                                .collectAsStateWithLifecycle(initialValue = PrefConst.HAZE_BLUR_RADIUS_DEFAULT)

                            val hazeTintAlpha by settingsRepository.getHazeTintAlphaFlow()
                                .collectAsStateWithLifecycle(initialValue = PrefConst.HAZE_TINT_ALPHA_DEFAULT)

                            val hazeState = remember { HazeState() }
                            val hazeStyle = rememberHazeStyle(blurRadius = hazeBlurRadius.dp, tintAlpha = hazeTintAlpha)
                            SmsCodeNavHost(
                                navController = navController,
                                onBack = { finish() },
                                initialTab = requestedTab,
                                onInitialTabConsumed = { requestedTab = null },
                                modifier = Modifier,
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                            )

                        if (blockingStartupDialog == null && showPrivacyPolicyDialog) {
                            PrivacyPolicyDialog(
                                onDismiss = {},
                                onConfirm = {
                                    scope.launch { settingsRepository.setPrivacyPolicyAccepted(true) }
                                    showPrivacyPolicyDialog = false
                                },
                                onCancel = {
                                    scope.launch { settingsRepository.setPrivacyPolicyAccepted(false) }
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
                                                    FrameworkCompatibilityMonitor.FrameworkIssueType.KNOWN_INCOMPATIBLE_FRAMEWORK ->
                                                        getString(
                                                            R.string.framework_incompatibility_known_framework_message,
                                                            issue.frameworkInfo?.displayLabel
                                                                ?: getString(R.string.unknown),
                                                        )

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
                                                    Utils.showWebPage(this@MainActivity, updateState.release.htmlUrl)
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

                        // Overlay for Circular Reveal
                        if (isAnimating && screenshotBitmap != null && !screenshotBitmap!!.isRecycled) {
                            val bitmap = screenshotBitmap!!.asImageBitmap()
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // Use Offscreen to allow BlendMode.Clear to punch a hole
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent() // Draw the Old Screenshot

                                        // Calculate specific radius for time t
                                        val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
                                        val radius = maxRadius * revealAnim.value

                                        // Draw a transparent circle to reveal the new content underneath
                                        drawCircle(
                                            color = androidx.compose.ui.graphics.Color.Transparent,
                                            radius = radius,
                                            center = animationCenter,
                                            blendMode = BlendMode.Clear,
                                        )
                                    },
                            )
                        }
                            io.github.magisk317.relay.ui.common.DismissibleSnackbarHost(
                                hostState = appSnackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding(),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyStoredLanguage() {
        val languageTag = runBlocking { settingsRepository.getLanguageTag() }
        AppCompatDelegate.setApplicationLocales(
            if (languageTag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        playUpdateDelegate.onResume(this) {
            PackageUtils.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
        }
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
        if (autoUpdateChecked) return
        autoUpdateChecked = true

        lifecycleScope.launch {
            val updateSettings = settingsRepository.getAutoUpdateSettings()
            val enabled = updateSettings.enabled
            if (!enabled) return@launch
            val wifiOnly = updateSettings.wifiOnly
            val onWifi = PackageUtils.isOnWifi(this@MainActivity)
            if (!UpdatePolicy.shouldRunAutoCheck(enabled, wifiOnly, onWifi)) return@launch

            when (UpdatePolicy.resolveStartupTarget(PackageUtils.isInstalledFromPlay(this@MainActivity))) {
                UpdatePolicy.StartupTarget.PLAY -> {
                requestPlayUpdateInternal(silentIfNoUpdate = true, fallbackOnQueryFailure = false)
                }
                UpdatePolicy.StartupTarget.GITHUB -> {
                    // Startup GitHub check is handled by checkStartupGithubUpdateIfNeeded()
                }
            }
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
