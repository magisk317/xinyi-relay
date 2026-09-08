@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home.appconfig

import io.github.magisk317.uikit.common.showLatestSnackbar

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.ui.common.AppIconBitmapImage
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.scroll.ReportLazyListScrollToChrome
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.SearchOverlayContent
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.surface.rememberSearchOverlayState
import io.github.magisk317.uikit.surface.WorkspaceTrailingIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

private const val APP_LIST_PREFETCH_DISTANCE = 12
private const val BENCHMARK_APPS_LIST = "xinyi_benchmark_apps_list"
private val APP_CONFIG_ICON_SIZE = 40.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppConfigScreen(
    onBack: (() -> Unit)? = null,
    onAppClick: ((AppInfo) -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: AppConfigViewModel = koinViewModel(),
    scrollChromeState: ScrollChromeState? = null,
    isActive: Boolean = true,
    bottomContentPadding: Dp = 0.dp,
    benchmarkTagsEnabled: Boolean = true,
) {
    val workPolicy = appConfigPageWorkPolicy(isActive)
    val retainedUiStateFlow = remember(viewModel, isActive) {
        retainedAppConfigUiStateFlow(
            policy = appConfigPageWorkPolicy(isActive),
            uiState = viewModel.uiState,
        )
    }
    val uiState by retainedUiStateFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.uiState.value,
    )
    val apps = uiState.apps
    val isLoading = uiState.isLoading
    val hasMoreApps = uiState.hasMoreApps
    val hideSystemApps = uiState.hideSystemApps
    val currentSortOption = uiState.sortOption
    val appNotifyBindingCount = uiState.appNotifyBindingCount
    val appIcons = uiState.appIcons
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetIconPx = remember(density) {
        with(density) { APP_CONFIG_ICON_SIZE.roundToPx() }
    }
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("app_config") }
    val snackbarHostState = remember { SnackbarHostState() }

    var initialLoadingStarted by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var manualRefreshStartedAt by remember { mutableLongStateOf(0L) }
    var manualRefreshRequest by remember { mutableIntStateOf(0) }
    var handledManualRefreshRequest by remember { mutableIntStateOf(0) }
    // A trigger is an edge, not durable screen state. A newly created secondary screen starts at
    // the current counter so it does not replay a top-level reselect that happened in the past.
    var handledRefreshTrigger by rememberSaveable { mutableIntStateOf(refreshTrigger) }
    var showUsagePermissionDialog by remember { mutableStateOf(false) }
    val searchState = rememberSearchOverlayState(
        onSearchChange = { viewModel.doFilter(it) },
    )
    var showSettingsMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = searchState.expanded) { searchState.close() }
    LaunchedEffect(isActive) { if (!isActive) searchState.close() }

    val showLoading = rememberMinDurationLoading(
        actualLoading = isActive && isLoading && shouldShowInitialLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )

    LaunchedEffect(isActive, isLoading, shouldShowInitialLoading, initialLoadingStarted) {
        if (!isActive) return@LaunchedEffect
        if (!shouldShowInitialLoading) return@LaunchedEffect
        if (isLoading) {
            initialLoadingStarted = true
        } else if (initialLoadingStarted) {
            SessionLoadingRegistry.markShown("app_config")
        }
    }

    LaunchedEffect(isActive, isLoading, manualRefreshing) {
        if (!isActive) {
            manualRefreshing = false
            manualRefreshStartedAt = 0L
            return@LaunchedEffect
        }
        if (manualRefreshing && !isLoading) {
            val elapsed = if (manualRefreshStartedAt > 0L) {
                SystemClock.elapsedRealtime() - manualRefreshStartedAt
            } else {
                LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS
            }
            val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)
            manualRefreshing = false
            manualRefreshStartedAt = 0L
        }
    }

    LaunchedEffect(isActive, refreshTrigger, manualRefreshRequest) {
        if (!workPolicy.refreshData) return@LaunchedEffect

        val refreshTriggered = refreshTrigger > 0 && refreshTrigger != handledRefreshTrigger
        val manualRefreshTriggered = manualRefreshRequest != handledManualRefreshRequest
        if (refreshTriggered) {
            handledRefreshTrigger = refreshTrigger
        }
        if (manualRefreshTriggered) {
            handledManualRefreshRequest = manualRefreshRequest
        }
        val forceRefresh = refreshTriggered || manualRefreshTriggered
        if (forceRefresh) {
            manualRefreshStartedAt = SystemClock.elapsedRealtime()
            manualRefreshing = true
        }
        viewModel.refreshData(force = forceRefresh)
    }

    LaunchedEffect(isActive, apps, targetIconPx) {
        if (!workPolicy.preloadIcons) return@LaunchedEffect
        viewModel.preloadAppIcons(
            packageNames = apps.map { it.packageName },
            sizePx = targetIconPx,
        )
    }

    LaunchedEffect(isActive, viewModel) {
        if (!workPolicy.collectEvents) return@LaunchedEffect
        viewModel.events.collect { event ->
            when (event) {
                is AppConfigViewModel.AppConfigEvent.Error -> {
                    snackbarHostState.showLatestSnackbar(event.throwable.message ?: context.getString(R.string.save_failed))
                }

                is AppConfigViewModel.AppConfigEvent.ShowUsageStatsPermission -> {
                    showUsagePermissionDialog = true
                }
            }
        }
    }

    val listState = rememberLazyListState()
    ReportLazyListScrollToChrome(listState, scrollChromeState)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val defaultTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 156.dp
    val navigationBarPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val effectiveBottomPadding = maxOf(bottomContentPadding, navigationBarPadding)

    LaunchedEffect(isActive, listState, apps.size, hasMoreApps, manualRefreshing, showLoading) {
        if (!workPolicy.paginate) return@LaunchedEffect
        if (manualRefreshing || showLoading) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (!hasMoreApps || apps.isEmpty()) return@collect
                if (lastVisibleIndex >= apps.lastIndex - APP_LIST_PREFETCH_DISTANCE) {
                    viewModel.loadMoreApps()
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        OverlayHeaderScaffold(
            fallbackTopPadding = defaultTopPadding,
            bottomPadding = effectiveBottomPadding,
            headerOffsetY = scrollChromeState?.animatedHeaderOffsetY ?: 0f,
            onHeaderHeightChanged = { scrollChromeState?.headerHeightPx = it.toFloat() },
            overlayModifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            overlay = {
                SearchOverlayContent(
                    state = searchState,
                    title = stringResource(R.string.app_config_settings),
                    searchPlaceholder = stringResource(R.string.action_search),
                    navigationIcon = if (onBack != null) {
                        {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showSettingsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showSettingsMenu,
                                onDismissRequest = { showSettingsMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_label)) },
                                    trailingIcon = {
                                        RadioButton(
                                            selected = currentSortOption == AppConfigViewModel.SortOption.LABEL,
                                            onClick = null,
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOption(AppConfigViewModel.SortOption.LABEL)
                                        showSettingsMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_selection)) },
                                    trailingIcon = {
                                        RadioButton(
                                            selected = currentSortOption == AppConfigViewModel.SortOption.SELECTION,
                                            onClick = null,
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOption(AppConfigViewModel.SortOption.SELECTION)
                                        showSettingsMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_usage)) },
                                    trailingIcon = {
                                        RadioButton(
                                            selected = currentSortOption == AppConfigViewModel.SortOption.USAGE,
                                            onClick = null,
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOption(AppConfigViewModel.SortOption.USAGE)
                                        showSettingsMenu = false
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_hide_system_apps)) },
                                    trailingIcon = {
                                        Checkbox(checked = hideSystemApps, onCheckedChange = null)
                                    },
                                    onClick = {
                                        viewModel.setHideSystemApps(!hideSystemApps)
                                        showSettingsMenu = false
                                    },
                                )
                            }
                        }
                    },
                )
            },
            content = { overlayPadding ->
                val overlayTopPadding = overlayPadding.calculateTopPadding()
                PullToRefreshBox(
                    state = pullToRefreshState,
                    isRefreshing = manualRefreshing,
                    onRefresh = {
                        if (workPolicy.refreshData) {
                            manualRefreshRequest++
                        }
                    },
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = overlayTopPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                            isRefreshing = manualRefreshing,
                            state = pullToRefreshState,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (showLoading && !manualRefreshing) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PolygonMorphLoadingIndicator(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = overlayTopPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (benchmarkTagsEnabled) {
                                        Modifier.testTag(BENCHMARK_APPS_LIST)
                                    } else {
                                        Modifier
                                    },
                                )
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            state = listState,
                            contentPadding = PaddingValues(
                                top = overlayTopPadding,
                                bottom = overlayPadding.calculateBottomPadding(),
                            ),
                        ) {
                            items(
                                items = apps,
                                key = { app -> app.packageName },
                            ) { app ->
                                AppConfigItem(
                                    app = app,
                                    appBoundSenderCount = appNotifyBindingCount[app.packageName] ?: 0,
                                    appIcon = appIcons[app.packageName],
                                    onClick = { onAppClick?.invoke(app) },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            },
        )

        io.github.magisk317.uikit.common.DismissibleSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = effectiveBottomPadding),
        )
    }

    if (showUsagePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showUsagePermissionDialog = false },
            title = { Text(stringResource(R.string.action_sort_by_usage)) },
            text = { Text(stringResource(R.string.usage_permission_prompt)) },
            confirmButton = {
                Button(onClick = {
                    showUsagePermissionDialog = false
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: Exception) {
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsagePermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun AppConfigItem(
    app: AppInfo,
    appBoundSenderCount: Int,
    appIcon: Bitmap?,
    onClick: () -> Unit,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = when {
        app.blocked && app.forwarding -> {
            val base = MaterialTheme.colorScheme.secondaryContainer
            if (isDark) base.copy(alpha = 0.25f) else base.copy(alpha = 0.4f)
        }

        app.blocked -> {
            val base = MaterialTheme.colorScheme.errorContainer
            if (isDark) base.copy(alpha = 0.25f) else base.copy(alpha = 0.4f)
        }

        app.forwarding -> {
            val base = MaterialTheme.colorScheme.primaryContainer
            if (isDark) base.copy(alpha = 0.25f) else base.copy(alpha = 0.4f)
        }

        else -> Color.Transparent
    }

    WorkspaceListItem(
        containerColor = bgColor,
        onClick = onClick,
        leadingContent = {
            AppIconBitmapImage(
                bitmap = appIcon,
                size = APP_CONFIG_ICON_SIZE,
                contentDescription = null,
            )
        },
        trailingContent = {
            WorkspaceTrailingIcon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight)
        },
    ) {
        Text(
            text = app.label ?: app.packageName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = app.packageName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                R.string.app_notify_summary_line,
                if (app.blocked) {
                    stringResource(R.string.app_input_status_on)
                } else {
                    stringResource(R.string.app_input_status_off)
                },
                if (app.forwarding) {
                    stringResource(R.string.app_notify_status_on)
                } else {
                    stringResource(R.string.app_notify_status_off)
                },
                if (appBoundSenderCount <= 0) {
                    stringResource(R.string.app_notify_channel_global_summary_short)
                } else {
                    stringResource(R.string.app_notify_channel_bound_count_short, appBoundSenderCount)
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
