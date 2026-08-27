package io.github.magisk317.relay.ui.home

import io.github.magisk317.relay.ui.home.forward.AppForwardFilterScreen
import io.github.magisk317.relay.ui.home.forward.GlobalForwardFilterScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.magisk317.relay.backup.BackupSource
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.ui.home.appconfig.AppConfigDetailScreen
import io.github.magisk317.relay.ui.home.appconfig.AppConfigScreen
import io.github.magisk317.relay.ui.home.appconfig.AppConfigViewModel
import io.github.magisk317.relay.ui.home.appconfig.AppNotifySenderBindingScreen
import io.github.magisk317.relay.ui.home.forward.ForwardKeepAliveScreen
import io.github.magisk317.relay.ui.home.overview.OverviewScreen
import io.github.magisk317.relay.ui.home.relayconfig.InterceptScreen
import io.github.magisk317.relay.ui.home.relayconfig.RelayConfigScreen
import io.github.magisk317.relay.ui.home.relayconfig.RemoteAgentScreen
import io.github.magisk317.relay.ui.home.scheduled.ScheduledReminderScreen
import io.github.magisk317.relay.ui.home.settings.AdvancedScreen
import io.github.magisk317.relay.ui.home.settings.SettingsHomeScreen
import io.github.magisk317.relay.ui.home.verification.VerificationSettingsScreen
import io.github.magisk317.relay.ui.nav.AdvancedRoute
import io.github.magisk317.relay.ui.nav.AppConfigDetailRoute
import io.github.magisk317.relay.ui.nav.AppForwardFilterRoute
import io.github.magisk317.relay.ui.nav.AppNotifySenderBindingRoute
import io.github.magisk317.relay.ui.nav.AppRoutingRoute
import io.github.magisk317.relay.ui.nav.AppsManageRoute
import io.github.magisk317.relay.ui.nav.AppsRoute
import io.github.magisk317.relay.ui.nav.BlacklistHitsRoute
import io.github.magisk317.relay.ui.nav.CloudBackupRoute
import io.github.magisk317.relay.ui.nav.ForwardKeepAliveRoute
import io.github.magisk317.relay.ui.nav.GlobalForwardFilterRoute
import io.github.magisk317.relay.ui.nav.InterceptRoute
import io.github.magisk317.relay.ui.nav.OverviewRoute
import io.github.magisk317.relay.ui.nav.ROUTE_ORIGIN_ADVANCED
import io.github.magisk317.relay.ui.nav.ROUTE_ORIGIN_APPS
import io.github.magisk317.relay.ui.nav.ROUTE_ORIGIN_SETTINGS
import io.github.magisk317.relay.ui.nav.RecordsRoute
import io.github.magisk317.relay.ui.nav.RelayConfigRoute
import io.github.magisk317.relay.ui.nav.RemoteAgentRoute
import io.github.magisk317.relay.ui.nav.RuleConfigRoute
import io.github.magisk317.relay.ui.nav.RulesRoute
import io.github.magisk317.relay.ui.nav.ScheduledReminderRoute
import io.github.magisk317.relay.ui.nav.ScheduledTaskConfigRoute
import io.github.magisk317.relay.ui.nav.ScheduledTasksRoute
import io.github.magisk317.relay.ui.nav.ScopedRecordsRoute
import io.github.magisk317.relay.ui.nav.SenderConfigRoute
import io.github.magisk317.relay.ui.nav.SenderForwardFilterRoute
import io.github.magisk317.relay.ui.nav.SenderTypeRoute
import io.github.magisk317.relay.ui.nav.SendersRoute
import io.github.magisk317.relay.ui.nav.SettingsRoute
import io.github.magisk317.relay.ui.nav.SmsCodeRuleEditorRoute
import io.github.magisk317.relay.ui.nav.SmsCodeRuleSourceRoute
import io.github.magisk317.relay.ui.nav.SmsCodeRulesRoute
import io.github.magisk317.relay.ui.nav.VerificationSettingsRoute
import io.github.magisk317.relay.ui.record.BlacklistHitListScreen
import io.github.magisk317.relay.ui.record.CodeRecordScreen
import io.github.magisk317.uikit.pager.rememberMainPagerState
import io.github.magisk317.uikit.surface.MainTabScaffold
import io.github.magisk317.uikit.surface.PagerTabScaffold
import io.github.magisk317.uikit.surface.tabEnterTransition
import io.github.magisk317.uikit.surface.tabExitTransition
import io.github.magisk317.uikit.surface.tabPopEnterTransition
import io.github.magisk317.uikit.surface.tabPopExitTransition
import io.github.magisk317.uikit.surface.tabPredictivePopEnterTransition
import io.github.magisk317.uikit.surface.tabPredictivePopExitTransition
import io.github.magisk317.uikit.surface.tabTransitionDirection
import io.github.magisk317.uikit.surface.MainTabSpec
import io.github.magisk317.uikit.surface.rememberIsCompactWidth
import io.github.magisk317.uikit.surface.rememberMainChromeController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import org.koin.compose.viewmodel.koinViewModel

private enum class NavigationSection {
    OVERVIEW,
    APPS,
    RECORDS,
    ADVANCED,
    SETTINGS,
}

private const val BENCHMARK_TAB_OVERVIEW = "xinyi_benchmark_tab_overview"
private const val BENCHMARK_TAB_APPS = "xinyi_benchmark_tab_apps"
private const val BENCHMARK_TAB_RECORDS = "xinyi_benchmark_tab_records"
private const val BENCHMARK_TAB_ADVANCED = "xinyi_benchmark_tab_advanced"
private const val BENCHMARK_TAB_SETTINGS = "xinyi_benchmark_tab_settings"
private const val BENCHMARK_RECORDS_SWIPE_ZONE = "xinyi_benchmark_records_swipe_zone"
private const val BENCHMARK_NAV_OVERVIEW = "xinyi_benchmark_nav_overview"
private const val BENCHMARK_NAV_APPS = "xinyi_benchmark_nav_apps"
private const val BENCHMARK_NAV_RECORDS = "xinyi_benchmark_nav_records"
private const val BENCHMARK_NAV_ADVANCED = "xinyi_benchmark_nav_advanced"
private const val BENCHMARK_NAV_SETTINGS = "xinyi_benchmark_nav_settings"

@Composable
@Suppress("CyclomaticComplexMethod")
fun MainScreen(
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    onBottomContentPaddingChanged: (Dp) -> Unit = {},
) {
    val navController = rememberNavController()
    val appConfigViewModel: AppConfigViewModel = koinViewModel()
    val settingsViewModel = io.github.magisk317.relay.ui.home.settings.rememberSharedSettingsViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = listOf(
        MainTabSpec(
            label = stringResource(R.string.tab_overview),
            icon = Icons.Default.Home,
            testTag = BENCHMARK_NAV_OVERVIEW,
        ),
        MainTabSpec(
            label = stringResource(R.string.tab_blacklist),
            icon = Icons.AutoMirrored.Filled.List,
            testTag = BENCHMARK_NAV_APPS,
        ),
        MainTabSpec(
            label = stringResource(R.string.tab_records),
            icon = Icons.Default.DateRange,
            testTag = BENCHMARK_NAV_RECORDS,
        ),
        MainTabSpec(
            label = stringResource(R.string.tab_advanced),
            icon = Icons.Default.Build,
            testTag = BENCHMARK_NAV_ADVANCED,
        ),
        MainTabSpec(
            label = stringResource(R.string.tab_settings),
            icon = Icons.Default.Settings,
            testTag = BENCHMARK_NAV_SETTINGS,
        ),
    )
    val tabRoutes = listOf(
        OverviewRoute,
        AppsRoute,
        RecordsRoute,
        AdvancedRoute,
        SettingsRoute,
    )

    fun sectionFromOrigin(origin: String): NavigationSection {
        return when (origin) {
            ROUTE_ORIGIN_SETTINGS -> NavigationSection.SETTINGS
            ROUTE_ORIGIN_APPS -> NavigationSection.APPS
            else -> NavigationSection.ADVANCED
        }
    }

    fun resolveSection(entry: NavBackStackEntry?): NavigationSection {
        val destination = entry?.destination ?: return NavigationSection.OVERVIEW
        return when {
            destination.hasRoute(OverviewRoute::class) -> NavigationSection.OVERVIEW
            destination.hasRoute(AppsRoute::class) -> NavigationSection.APPS
            destination.hasRoute(AppsManageRoute::class) -> NavigationSection.APPS
            destination.hasRoute(RecordsRoute::class) -> NavigationSection.RECORDS
            destination.hasRoute(AdvancedRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(SettingsRoute::class) -> NavigationSection.SETTINGS
            destination.hasRoute(VerificationSettingsRoute::class) -> NavigationSection.SETTINGS
            destination.hasRoute(RemoteAgentRoute::class) -> NavigationSection.SETTINGS
            destination.hasRoute(InterceptRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(BlacklistHitsRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(ScheduledReminderRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(ScheduledTasksRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(ScheduledTaskConfigRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(ForwardKeepAliveRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(RelayConfigRoute::class) ->
                sectionFromOrigin(entry.toRoute<RelayConfigRoute>().origin)
            destination.hasRoute(SendersRoute::class) ->
                sectionFromOrigin(entry.toRoute<SendersRoute>().origin)
            destination.hasRoute(SenderConfigRoute::class) ->
                sectionFromOrigin(entry.toRoute<SenderConfigRoute>().origin)
            destination.hasRoute(SenderForwardFilterRoute::class) ->
                sectionFromOrigin(entry.toRoute<SenderForwardFilterRoute>().origin)
            destination.hasRoute(AppRoutingRoute::class) ->
                sectionFromOrigin(entry.toRoute<AppRoutingRoute>().origin)
            destination.hasRoute(AppConfigDetailRoute::class) ->
                sectionFromOrigin(entry.toRoute<AppConfigDetailRoute>().origin)
            destination.hasRoute(AppNotifySenderBindingRoute::class) ->
                sectionFromOrigin(entry.toRoute<AppNotifySenderBindingRoute>().origin)
            destination.hasRoute(AppForwardFilterRoute::class) ->
                sectionFromOrigin(entry.toRoute<AppForwardFilterRoute>().origin)
            destination.hasRoute(ScopedRecordsRoute::class) ->
                sectionFromOrigin(entry.toRoute<ScopedRecordsRoute>().origin)
            destination.hasRoute(RulesRoute::class) ->
                sectionFromOrigin(entry.toRoute<RulesRoute>().origin)
            destination.hasRoute(RuleConfigRoute::class) ->
                sectionFromOrigin(entry.toRoute<RuleConfigRoute>().origin)
            destination.hasRoute(SmsCodeRulesRoute::class) ->
                sectionFromOrigin(entry.toRoute<SmsCodeRulesRoute>().origin)
            destination.hasRoute(SmsCodeRuleEditorRoute::class) ->
                sectionFromOrigin(entry.toRoute<SmsCodeRuleEditorRoute>().origin)
            destination.hasRoute(SmsCodeRuleSourceRoute::class) ->
                sectionFromOrigin(entry.toRoute<SmsCodeRuleSourceRoute>().origin)
            destination.hasRoute(CloudBackupRoute::class) -> NavigationSection.SETTINGS
            destination.hasRoute(GlobalForwardFilterRoute::class) ->
                sectionFromOrigin(entry.toRoute<GlobalForwardFilterRoute>().origin)
            destination.hasRoute(SenderTypeRoute::class) ->
                sectionFromOrigin(entry.toRoute<SenderTypeRoute>().origin)
            else -> NavigationSection.OVERVIEW
        }
    }

    fun resolveTabIndex(entry: NavBackStackEntry?): Int {
        return when (resolveSection(entry)) {
            NavigationSection.OVERVIEW -> 0
            NavigationSection.APPS -> 1
            NavigationSection.RECORDS -> 2
            NavigationSection.ADVANCED -> 3
            NavigationSection.SETTINGS -> 4
        }
    }

    fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
        if (destination == null) return true
        return destination.hasRoute(OverviewRoute::class) ||
            destination.hasRoute(AppsRoute::class) ||
            destination.hasRoute(RecordsRoute::class) ||
            destination.hasRoute(AdvancedRoute::class) ||
            destination.hasRoute(SettingsRoute::class)
    }

    fun shouldAllowScrollChrome(entry: NavBackStackEntry?): Boolean {
        val destination = entry?.destination ?: return false
        return destination.hasRoute(AppsRoute::class) ||
            destination.hasRoute(RecordsRoute::class) ||
            destination.hasRoute(AppsManageRoute::class) ||
            destination.hasRoute(AppRoutingRoute::class) ||
            destination.hasRoute(ScopedRecordsRoute::class)
    }

    val selectedIndex = resolveTabIndex(navBackStackEntry)
    val isTopLevelRoute = currentDestination != null && shouldShowCompactBottomBar(currentDestination)
    val pagerState = rememberMainPagerState(
        pageCount = { tabRoutes.size },
        initialPage = selectedIndex,
    )
    val pagerIsInMotion = pagerState.isNavigating || pagerState.pagerState.isScrollInProgress
    val pagerSync = remember { TopLevelRoutePagerSynchronizer(tabRoutes.size) }
    LaunchedEffect(pagerState.pagerState.currentPage) {
        // User drags update PagerState directly. Mirror that page into MainPagerState so the
        // selected navigation item and a later click are never left on the pre-drag page.
        pagerState.syncPage()
    }
    PredictiveBackHandler(
        enabled = topLevelBackHandlerEnabled(
            isTopLevelRoute = isTopLevelRoute,
            selectedPage = pagerState.selectedPage,
            settledPage = pagerState.settledPage,
            routePage = selectedIndex,
        ),
    ) { progress ->
        try {
            // Participate in predictive-back dispatch even though the pager owns its own spring
            // animation. Completion returns to Overview; cancellation leaves the page unchanged.
            progress.collect { }
            pagerState.animateToPage(0)
        } catch (_: CancellationException) {
            // The gesture was cancelled or this top-level handler stopped owning back.
        }
    }
    val isCompact = rememberIsCompactWidth()
    var appBlockRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var recordsRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var interceptRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var reportedBottomContentPadding by remember { mutableStateOf(0.dp) }
    LaunchedEffect(isTopLevelRoute, reportedBottomContentPadding) {
        onBottomContentPaddingChanged(
            if (isTopLevelRoute) reportedBottomContentPadding else 0.dp,
        )
    }

    val currentSection = resolveSection(navBackStackEntry)
    val allowScrollChrome = shouldAllowScrollChrome(navBackStackEntry)
    val chromeResetKey = if (isTopLevelRoute) "main-tabs" else "${currentDestination?.route}:${currentSection.name}"
    val chromeController = rememberMainChromeController(
        isCompact = isCompact,
        compactChromeRouteAvailable = shouldShowCompactBottomBar(currentDestination),
        keepVisible = !allowScrollChrome,
        allowScrollHide = allowScrollChrome,
        resetKey = chromeResetKey,
    )
    val pageScrollChromeState = chromeController.pageScrollChromeState
    var lastChromePage by remember { mutableIntStateOf(pagerState.settledPage) }
    LaunchedEffect(isTopLevelRoute, pagerState.settledPage) {
        if (isTopLevelRoute && lastChromePage != pagerState.settledPage) {
            // A user swipe bypasses MainTabScaffold's click handler, so explicitly reset the
            // shared collapsing chrome when ownership moves to another retained page.
            chromeController.scrollChromeState.animateToTop()
        }
        lastChromePage = pagerState.settledPage
    }

    fun triggerRefreshForIndex(index: Int) {
        when (index) {
            1 -> appBlockRefreshTrigger++
            2 -> recordsRefreshTrigger++
        }
    }

    fun navigateToTab(index: Int) {
        val route = tabRoutes.getOrNull(index) ?: return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(selectedIndex, currentDestination, pagerState.settledPage, pagerIsInMotion) {
        when (val action = pagerSync.reconcile(
            routePage = selectedIndex,
            isTopLevel = isTopLevelRoute,
            settledPage = pagerState.settledPage,
            isPagerInMotion = pagerIsInMotion,
        )) {
            is TopLevelPagerSyncAction.MovePager -> {
                if (action.snap) pagerState.pagerState.scrollToPage(action.page)
                else pagerState.animateToPage(action.page)
            }
            is TopLevelPagerSyncAction.PublishRoute -> navigateToTab(action.page)
            TopLevelPagerSyncAction.None -> Unit
        }
    }

    LaunchedEffect(initialTab) {
        when (initialTab) {
            is OverviewRoute -> navController.navigate(OverviewRoute)
            is AppsRoute -> navController.navigate(AppsRoute)
            is AppsManageRoute -> navController.navigate(AppsManageRoute)
            is InterceptRoute -> navController.navigate(InterceptRoute)
            is RecordsRoute -> navController.navigate(RecordsRoute)
            is AdvancedRoute -> navController.navigate(AdvancedRoute)
            is SettingsRoute -> navController.navigate(SettingsRoute)
            is SmsCodeRulesRoute -> navController.navigate(initialTab)
            is SmsCodeRuleEditorRoute -> navController.navigate(initialTab)
            else -> Unit
        }
        if (initialTab != null) {
            onInitialTabConsumed?.invoke()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        PagerTabScaffold(
            tabs = tabs,
            pagerState = pagerState,
            isCompact = isCompact,
            chromeController = chromeController,
            onTabReselected = { index -> triggerRefreshForIndex(index) },
            beyondViewportPageCount = 1,
            userScrollEnabled = isTopLevelRoute,
            reserveCompactBottomBarSpace = true,
            retainPageContentAfterFirstFrame = true,
            railHeader = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .blockUnderlyingPager(blocked = !isTopLevelRoute),
        ) { page, contentPadding ->
            val contentBottomPadding = contentPadding.calculateBottomPadding()
            SideEffect {
                if (isTopLevelRoute) {
                    reportedBottomContentPadding = contentBottomPadding
                }
            }
            val pageActive = isTopLevelRoute &&
                isTopLevelPageActive(page, pagerState.settledPage)
            val pageChromeState = if (
                isTopLevelRoute && isTopLevelPageChromeOwner(page, pagerState.currentPage)
            ) {
                pageScrollChromeState
            } else {
                null
            }
            when (page) {
                0 -> Box(
                    Modifier
                        .fillMaxSize()
                        .then(benchmarkModifier(pageActive, BENCHMARK_TAB_OVERVIEW)),
                ) {
                    OverviewScreen(
                        isActive = pageActive,
                        bottomContentPadding = contentBottomPadding,
                    )
                }

                1 -> Box(
                    Modifier
                        .fillMaxSize()
                        .then(benchmarkModifier(pageActive, BENCHMARK_TAB_APPS)),
                ) {
                    AppConfigScreen(
                        onBack = null,
                        onAppClick = { app ->
                            navController.navigate(
                                AppConfigDetailRoute(
                                    packageName = app.packageName,
                                    origin = ROUTE_ORIGIN_APPS,
                                ),
                            )
                        },
                        refreshTrigger = appBlockRefreshTrigger,
                        viewModel = appConfigViewModel,
                        scrollChromeState = pageChromeState,
                        isActive = pageActive,
                        bottomContentPadding = contentBottomPadding,
                        benchmarkTagsEnabled = pageActive,
                    )
                }

                2 -> Box(
                    Modifier
                        .fillMaxSize()
                        .then(benchmarkModifier(pageActive, BENCHMARK_TAB_RECORDS)),
                ) {
                    CodeRecordScreen(
                        onBack = null,
                        refreshTrigger = recordsRefreshTrigger,
                        scrollChromeState = pageChromeState,
                        isActive = pageActive,
                        bottomContentPadding = contentBottomPadding,
                        benchmarkTagsEnabled = pageActive,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(112.dp)
                            .then(
                                benchmarkModifier(
                                    enabled = pageActive,
                                    tag = BENCHMARK_RECORDS_SWIPE_ZONE,
                                ),
                            ),
                    )
                }

                3 -> Box(
                    Modifier
                        .fillMaxSize()
                        .then(benchmarkModifier(pageActive, BENCHMARK_TAB_ADVANCED)),
                ) {
                    AdvancedScreen(
                        bottomContentPadding = contentBottomPadding,
                        isActive = pageActive,
                        benchmarkTagsEnabled = pageActive,
                        onInterceptClick = { navController.navigate(InterceptRoute) },
                        onVerificationConfigClick = {
                            navController.navigate(VerificationSettingsRoute)
                        },
                        onRelayConfigClick = {
                            navController.navigate(RelayConfigRoute(origin = ROUTE_ORIGIN_ADVANCED))
                        },
                        onForwardKeepAliveClick = { navController.navigate(ForwardKeepAliveRoute) },
                        onScheduledReminderClick = { navController.navigate(ScheduledReminderRoute) },
                        onRemoteAgentClick = { navController.navigate(RemoteAgentRoute) },
                        onNavigateToScheduledTasks = if (BuildConfig.ENABLE_SMS_CHANNEL) {
                            { navController.navigate(ScheduledTasksRoute) }
                        } else {
                            null
                        },
                    )
                }

                4 -> Box(
                    Modifier
                        .fillMaxSize()
                        .then(benchmarkModifier(pageActive, BENCHMARK_TAB_SETTINGS)),
                ) {
                    SettingsHomeScreen(
                        isActive = pageActive,
                        bottomContentPadding = contentBottomPadding,
                        onOpenVerification = { navController.navigate(VerificationSettingsRoute) },
                        onOpenAdvancedRelay = {
                            navController.navigate(RelayConfigRoute(origin = ROUTE_ORIGIN_SETTINGS))
                        },
                        onOpenCloudBackup = { source, backupNow ->
                            navController.navigate(
                                CloudBackupRoute(
                                    initialSource = source?.name,
                                    backupNow = backupNow,
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (!isTopLevelRoute) {
            // Mount the typed graph before the first top-level frame and keep all secondary
            // destinations outside the horizontal pager. The NavController retains this graph
            // while top-level pages are rendered directly by PagerTabScaffold.
            MainTabScaffold(
                tabs = tabs,
                selectedIndex = selectedIndex,
                isCompact = isCompact,
                chromeController = chromeController,
                onTabSelected = ::navigateToTab,
                onTabReselected = { index -> triggerRefreshForIndex(index) },
                showSystemBarsScrim = false,
                railHeader = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) { contentPadding ->
                val contentBottomPadding = contentPadding.calculateBottomPadding()
                SideEffect {
                    reportedBottomContentPadding = contentBottomPadding
                }
                NavHost(
                    navController = navController,
                    startDestination = OverviewRoute,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = contentBottomPadding),
                    enterTransition = {
                        tabEnterTransition(
                            tabTransitionDirection(
                                initialIndex = resolveTabIndex(initialState),
                                targetIndex = resolveTabIndex(targetState),
                            ),
                        )
                    },
                    exitTransition = {
                        tabExitTransition(
                            tabTransitionDirection(
                                initialIndex = resolveTabIndex(initialState),
                                targetIndex = resolveTabIndex(targetState),
                            ),
                        )
                    },
                    popEnterTransition = {
                        tabPopEnterTransition(
                            tabTransitionDirection(
                                initialIndex = resolveTabIndex(initialState),
                                targetIndex = resolveTabIndex(targetState),
                            ),
                        )
                    },
                    popExitTransition = {
                        tabPopExitTransition(
                            tabTransitionDirection(
                                initialIndex = resolveTabIndex(initialState),
                                targetIndex = resolveTabIndex(targetState),
                            ),
                        )
                    },
                    predictivePopEnterTransition = { _ ->
                        tabPredictivePopEnterTransition()
                    },
                    predictivePopExitTransition = { swipeEdge ->
                        tabPredictivePopExitTransition(swipeEdge)
                    },
                ) {
                    // Top-level destinations only keep the typed back stack coherent. Their
                    // retained UI is the pager layer below this NavHost, so predictive back can
                    // reveal the real page without starting duplicate page work.
                    composable<OverviewRoute> { Box(Modifier.fillMaxSize()) }
                    composable<AppsRoute> { Box(Modifier.fillMaxSize()) }
                    composable<RecordsRoute> { Box(Modifier.fillMaxSize()) }
                    composable<AdvancedRoute> { Box(Modifier.fillMaxSize()) }
                    composable<SettingsRoute> { Box(Modifier.fillMaxSize()) }
                    composable<AppsManageRoute> {
                        AppConfigScreen(
                            onBack = { navController.popBackStack() },
                            onAppClick = { app ->
                                navController.navigate(
                                    AppConfigDetailRoute(
                                        packageName = app.packageName,
                                        origin = ROUTE_ORIGIN_APPS,
                                    ),
                                )
                            },
                            refreshTrigger = appBlockRefreshTrigger,
                            viewModel = appConfigViewModel,
                            scrollChromeState = pageScrollChromeState,
                        )
                    }
                    composable<AppConfigDetailRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<AppConfigDetailRoute>()
                        AppConfigDetailScreen(
                            packageName = route.packageName,
                            onBack = { navController.popBackStack() },
                            onConfigureNotifyChannels = {
                                navController.navigate(
                                    AppNotifySenderBindingRoute(
                                        packageName = route.packageName,
                                        origin = route.origin,
                                    ),
                                )
                            },
                            onConfigureForwardFilters = {
                                navController.navigate(
                                    AppForwardFilterRoute(
                                        packageName = route.packageName,
                                        origin = route.origin,
                                    ),
                                )
                            },
                            viewModel = appConfigViewModel,
                        )
                    }
                    composable<AppNotifySenderBindingRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<AppNotifySenderBindingRoute>()
                        AppNotifySenderBindingScreen(
                            packageName = route.packageName,
                            onBack = { navController.popBackStack() },
                            viewModel = appConfigViewModel,
                        )
                    }
                    composable<AppForwardFilterRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<AppForwardFilterRoute>()
                        AppForwardFilterScreen(
                            packageName = route.packageName,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<InterceptRoute> {
                        InterceptScreen(
                            refreshTrigger = interceptRefreshTrigger,
                            onOpenBlacklistHits = { navController.navigate(BlacklistHitsRoute) },
                        )
                    }
                    composable<BlacklistHitsRoute> {
                        BlacklistHitListScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<ScopedRecordsRoute> {
                        CodeRecordScreen(
                            onBack = { navController.popBackStack() },
                            refreshTrigger = recordsRefreshTrigger,
                            scrollChromeState = pageScrollChromeState,
                        )
                    }
                    composable<ScheduledTasksRoute> {
                        io.github.magisk317.relay.ui.scheduled.ScheduledTasksScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToConfig = { taskId ->
                                navController.navigate(ScheduledTaskConfigRoute(id = taskId))
                            }
                        )
                    }
                    composable<ScheduledTaskConfigRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<ScheduledTaskConfigRoute>()
                        io.github.magisk317.relay.ui.scheduled.ScheduledTaskConfigScreen(
                            taskId = route.id,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<RelayConfigRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<RelayConfigRoute>()
                        RelayConfigScreen(
                            onBack = { navController.popBackStack() },
                            onOpenSenders = {
                                navController.navigate(SendersRoute(origin = route.origin))
                            },
                            onOpenAppRouting = {
                                navController.navigate(AppRoutingRoute(origin = route.origin))
                            },
                            onOpenFilters = {
                                navController.navigate(GlobalForwardFilterRoute(origin = route.origin))
                            },
                            onOpenRecords = {
                                navController.navigate(ScopedRecordsRoute(origin = route.origin))
                            },
                        )
                    }
                    composable<ScheduledReminderRoute> {
                        ScheduledReminderScreen(onBack = { navController.popBackStack() })
                    }
                    composable<ForwardKeepAliveRoute> {
                        ForwardKeepAliveScreen(onBack = { navController.popBackStack() })
                    }
                    composable<GlobalForwardFilterRoute> {
                        GlobalForwardFilterScreen(onBack = { navController.popBackStack() })
                    }
                    composable<SendersRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SendersRoute>()
                        io.github.magisk317.relay.ui.sender.SenderListScreen(
                            onAddClick = {
                                navController.navigate(SenderTypeRoute(origin = route.origin))
                            },
                            onEditClick = { id ->
                                navController.navigate(
                                    SenderConfigRoute(
                                        id = id,
                                        type = 1,
                                        origin = route.origin,
                                    ),
                                )
                            }
                        )
                    }
                    composable<SenderTypeRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SenderTypeRoute>()
                        io.github.magisk317.relay.ui.sender.SenderTypeScreen(
                            onBack = { navController.popBackStack() },
                            onAddClick = { type ->
                                navController.navigate(
                                    SenderConfigRoute(
                                        id = 0L,
                                        type = type,
                                        origin = route.origin,
                                    ),
                                )
                            }
                        )
                    }
                    composable<AppRoutingRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<AppRoutingRoute>()
                        AppConfigScreen(
                            onBack = { navController.popBackStack() },
                            onAppClick = { app ->
                                navController.navigate(
                                    AppConfigDetailRoute(
                                        packageName = app.packageName,
                                        origin = route.origin,
                                    ),
                                )
                            },
                            refreshTrigger = appBlockRefreshTrigger,
                            viewModel = appConfigViewModel,
                            scrollChromeState = pageScrollChromeState,
                        )
                    }
                    composable<SenderConfigRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SenderConfigRoute>()
                        io.github.magisk317.relay.ui.sender.SenderConfigScreen(
                            senderId = route.id,
                            senderTypeArg = route.type,
                            onOpenSenderNotifyScope = {
                                navController.navigate(AppRoutingRoute(origin = route.origin))
                            },
                            onOpenSenderForwardFilter = { senderId ->
                                navController.navigate(
                                    SenderForwardFilterRoute(
                                        senderId = senderId,
                                        origin = route.origin,
                                    ),
                                )
                            },
                            onBack = { reopenTypeDialog ->
                                if (reopenTypeDialog) {
                                    navController.popBackStack()
                                } else {
                                    navController.popBackStack(SendersRoute::class, inclusive = false)
                                }
                            }
                        )
                    }
                    composable<SenderForwardFilterRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SenderForwardFilterRoute>()
                        io.github.magisk317.relay.ui.sender.SenderForwardFilterScreen(
                            senderId = route.senderId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<RulesRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<RulesRoute>()
                        io.github.magisk317.relay.ui.rule.RuleListScreen(
                            senderId = route.senderId,
                            onAddClick = {
                                navController.navigate(
                                    RuleConfigRoute(id = 0L, origin = route.origin),
                                )
                            },
                            onEditClick = { id ->
                                navController.navigate(
                                    RuleConfigRoute(id = id, origin = route.origin),
                                )
                            },
                        )
                    }
                        composable<RuleConfigRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<RuleConfigRoute>()
                            io.github.magisk317.relay.ui.rule.RuleConfigScreen(
                                ruleId = route.id,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    composable<SmsCodeRulesRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SmsCodeRulesRoute>()
                        io.github.magisk317.relay.ui.smscoderule.SmsCodeRuleListScreen(
                            onBack = { navController.popBackStack() },
                            onAddClick = {
                                navController.navigate(
                                    SmsCodeRuleEditorRoute(origin = route.origin),
                                )
                            },
                            onEditClick = { id ->
                                navController.navigate(
                                    SmsCodeRuleEditorRoute(id = id, origin = route.origin),
                                )
                            },
                            onSourceSettingsClick = {
                                navController.navigate(SmsCodeRuleSourceRoute(origin = route.origin))
                            },
                        )
                    }
                    composable<SmsCodeRuleEditorRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SmsCodeRuleEditorRoute>()
                        io.github.magisk317.relay.ui.smscoderule.SmsCodeRuleEditorScreen(
                            ruleId = route.id,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<SmsCodeRuleSourceRoute> {
                        io.github.magisk317.relay.ui.smscoderule.SmsCodeRuleSourceSettingsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<VerificationSettingsRoute> {
                        VerificationSettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenRules = {
                                navController.navigate(SmsCodeRulesRoute(origin = ROUTE_ORIGIN_SETTINGS))
                            },
                            onOpenRecords = {
                                navController.navigate(ScopedRecordsRoute(origin = ROUTE_ORIGIN_SETTINGS))
                            },
                        )
                    }
                    composable<RemoteAgentRoute> {
                        RemoteAgentScreen(onBack = { navController.popBackStack() })
                    }
                    composable<CloudBackupRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<CloudBackupRoute>()
                        val initialSource = route.initialSource?.let(::parseBackupSource)
                        io.github.magisk317.relay.ui.backup.CloudBackupScreen(
                            onBack = { navController.popBackStack() },
                            initialSource = initialSource,
                            backupNow = route.backupNow,
                        )
                    }

                }
            }
        }
    }
}

private fun benchmarkModifier(enabled: Boolean, tag: String): Modifier =
    Modifier.then(if (enabled) Modifier.testTag(tag) else Modifier)

private fun Modifier.blockUnderlyingPager(blocked: Boolean): Modifier {
    if (!blocked) return this
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change ->
                    change.consume()
                }
            }
        }
    }.clearAndSetSemantics { }
}

private fun parseBackupSource(rawSource: String): BackupSource? {
    return BackupSource.entries.firstOrNull { it.name == rawSource }
}
