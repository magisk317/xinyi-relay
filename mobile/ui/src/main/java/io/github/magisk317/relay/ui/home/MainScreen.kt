package io.github.magisk317.relay.ui.home

import io.github.magisk317.relay.ui.home.forward.AppForwardFilterScreen
import io.github.magisk317.relay.ui.home.forward.GlobalForwardFilterScreen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AnimatedCompactBottomNavigationChrome
import io.github.magisk317.uikit.surface.AnimatedSystemBarsScrim
import io.github.magisk317.uikit.surface.AppNavigationItemSpec
import io.github.magisk317.uikit.surface.AppNavigationRail
import io.github.magisk317.uikit.surface.TabItem
import io.github.magisk317.uikit.surface.rememberIsCompactWidth
import io.github.magisk317.uikit.surface.rememberMainChromeController
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.nav.*
import io.github.magisk317.relay.ui.record.CodeRecordScreen
import android.os.SystemClock
import io.github.magisk317.relay.backup.BackupSource
import io.github.magisk317.relay.mobileui.BuildConfig
import org.koin.compose.viewmodel.koinViewModel
import io.github.magisk317.relay.ui.home.overview.OverviewScreen
import io.github.magisk317.relay.ui.home.appconfig.AppConfigDetailScreen
import io.github.magisk317.relay.ui.home.verification.VerificationSettingsScreen
import io.github.magisk317.relay.ui.home.settings.SettingsHomeScreen
import io.github.magisk317.relay.ui.home.appconfig.AppConfigViewModel
import io.github.magisk317.relay.ui.home.forward.ForwardKeepAliveScreen
import io.github.magisk317.relay.ui.home.relayconfig.RemoteAgentScreen
import io.github.magisk317.relay.ui.record.BlacklistHitListScreen
import io.github.magisk317.relay.ui.home.relayconfig.InterceptScreen
import io.github.magisk317.relay.ui.home.scheduled.ScheduledReminderScreen
import io.github.magisk317.relay.ui.home.appconfig.AppNotifySenderBindingScreen
import io.github.magisk317.relay.ui.home.settings.AdvancedScreen
import io.github.magisk317.relay.ui.home.appconfig.AppConfigScreen
import io.github.magisk317.relay.ui.home.relayconfig.RelayConfigScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private const val TAB_DOUBLE_TAP_REFRESH_WINDOW_MS = 350L
private val COMPACT_BOTTOM_BAR_CONTENT_PADDING = 80.dp

private enum class NavigationSection {
    OVERVIEW,
    APPS,
    RECORDS,
    ADVANCED,
    SETTINGS;

    val routeId: String
        get() = when (this) {
            OVERVIEW -> MAIN_TAB_OVERVIEW
            APPS -> MAIN_TAB_APPS
            RECORDS -> MAIN_TAB_RECORDS
            ADVANCED -> MAIN_TAB_ADVANCED
            SETTINGS -> MAIN_TAB_SETTINGS
        }

    companion object {
        fun fromRouteId(routeId: String): NavigationSection {
            return entries.firstOrNull { it.routeId == routeId } ?: OVERVIEW
        }
    }
}

private const val MAIN_TAB_OVERVIEW = "overview"
private const val MAIN_TAB_APPS = "apps"
private const val MAIN_TAB_RECORDS = "records"
private const val MAIN_TAB_ADVANCED = "advanced"
private const val MAIN_TAB_SETTINGS = "settings"
private const val BENCHMARK_TAB_OVERVIEW = "xinyi_benchmark_tab_overview"
private const val BENCHMARK_TAB_APPS = "xinyi_benchmark_tab_apps"
private const val BENCHMARK_TAB_RECORDS = "xinyi_benchmark_tab_records"
private const val BENCHMARK_TAB_ADVANCED = "xinyi_benchmark_tab_advanced"
private const val BENCHMARK_TAB_SETTINGS = "xinyi_benchmark_tab_settings"
private const val BENCHMARK_NAV_OVERVIEW = "xinyi_benchmark_nav_overview"
private const val BENCHMARK_NAV_APPS = "xinyi_benchmark_nav_apps"
private const val BENCHMARK_NAV_RECORDS = "xinyi_benchmark_nav_records"
private const val BENCHMARK_NAV_ADVANCED = "xinyi_benchmark_nav_advanced"
private const val BENCHMARK_NAV_SETTINGS = "xinyi_benchmark_nav_settings"

@Serializable
private data class MainTabsRoute(
    val section: String = MAIN_TAB_OVERVIEW,
)

@Composable
@Suppress("CyclomaticComplexMethod")
fun MainScreen(
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
) {
    val navController = rememberNavController()
    val appConfigViewModel: AppConfigViewModel = koinViewModel()
    val settingsViewModel = io.github.magisk317.relay.ui.home.settings.rememberSharedSettingsViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = listOf(
        TabItem(stringResource(R.string.tab_overview), Icons.Default.Home, MainTabsRoute(MAIN_TAB_OVERVIEW)),
        TabItem(stringResource(R.string.tab_blacklist), Icons.AutoMirrored.Filled.List, MainTabsRoute(MAIN_TAB_APPS)),
        TabItem(stringResource(R.string.tab_records), Icons.Default.DateRange, MainTabsRoute(MAIN_TAB_RECORDS)),
        TabItem(stringResource(R.string.tab_advanced), Icons.Default.Build, MainTabsRoute(MAIN_TAB_ADVANCED)),
        TabItem(stringResource(R.string.tab_settings), Icons.Default.Settings, MainTabsRoute(MAIN_TAB_SETTINGS)),
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
            destination.hasRoute(MainTabsRoute::class) ->
                NavigationSection.fromRouteId(entry.toRoute<MainTabsRoute>().section)
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
            destination.hasRoute(GlobalForwardFilterRoute::class) ->
                sectionFromOrigin(entry.toRoute<GlobalForwardFilterRoute>().origin)
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
            destination.hasRoute(CloudBackupRoute::class) -> NavigationSection.SETTINGS
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
        return destination.hasRoute(MainTabsRoute::class)
    }

    fun shouldAllowScrollChrome(entry: NavBackStackEntry?): Boolean {
        val destination = entry?.destination ?: return false
        return when {
            destination.hasRoute(MainTabsRoute::class) -> {
                when (NavigationSection.fromRouteId(entry.toRoute<MainTabsRoute>().section)) {
                    NavigationSection.APPS, NavigationSection.RECORDS -> true
                    NavigationSection.OVERVIEW,
                    NavigationSection.ADVANCED,
                    NavigationSection.SETTINGS,
                    -> false
                }
            }
            destination.hasRoute(AppsRoute::class) ||
                destination.hasRoute(AppsManageRoute::class) ||
                destination.hasRoute(AppRoutingRoute::class) ||
                destination.hasRoute(ScopedRecordsRoute::class) -> true
            else -> false
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = resolveTabIndex(navBackStackEntry),
        pageCount = { NavigationSection.entries.size },
    )

    // On MainTabs the pager is the source of truth: launchSingleTop nav to the same
    // MainTabsRoute yields no new back-stack entry, so navBackStackEntry.section stays
    // pinned to the initial value. Fall back to route-origin derivation only on child screens.
    val onMainTabs = currentDestination?.hasRoute(MainTabsRoute::class) == true
    val selectedIndex = if (onMainTabs) {
        pagerState.targetPage
    } else {
        resolveTabIndex(navBackStackEntry)
    }

    val isCompact = rememberIsCompactWidth()
    var appBlockRefreshTrigger by remember { mutableIntStateOf(0) }
    var recordsRefreshTrigger by remember { mutableIntStateOf(0) }
    var interceptRefreshTrigger by remember { mutableIntStateOf(0) }
    val tabLastTapAt = remember { mutableStateMapOf<String, Long>() }

    // Likewise derive section from the pager so scroll chrome (only APPS/RECORDS hide on
    // scroll) tracks the visible tab instead of the stale initial section.
    val pagerSection = NavigationSection.entries[pagerState.targetPage]
    val currentSection = if (onMainTabs) pagerSection else resolveSection(navBackStackEntry)
    val allowScrollChrome = if (onMainTabs) {
        pagerSection == NavigationSection.APPS || pagerSection == NavigationSection.RECORDS
    } else {
        shouldAllowScrollChrome(navBackStackEntry)
    }
    val chromeController = rememberMainChromeController(
        isCompact = isCompact,
        compactChromeRouteAvailable = shouldShowCompactBottomBar(currentDestination),
        keepVisible = !allowScrollChrome,
        allowScrollHide = allowScrollChrome,
        resetKey = "${currentDestination?.route}:${currentSection.routeId}",
    )
    val scrollChromeState = chromeController.scrollChromeState
    val pageScrollChromeState = chromeController.pageScrollChromeState
    val mainChromeVisible = chromeController.mainChromeVisible
    val compactBottomBarVisible = chromeController.compactBottomBarVisible

    val compactBottomBarPadding: Dp = if (compactBottomBarVisible) {
        COMPACT_BOTTOM_BAR_CONTENT_PADDING +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }

    fun triggerRefreshForSection(section: NavigationSection) {
        when (section) {
            NavigationSection.APPS -> appBlockRefreshTrigger++
            NavigationSection.RECORDS -> recordsRefreshTrigger++
            else -> Unit
        }
    }

    fun handleTabClick(tab: TabItem<MainTabsRoute>, selected: Boolean) {
        val key = tab.route.section
        val now = SystemClock.elapsedRealtime()
        val last = tabLastTapAt[key] ?: 0L
        tabLastTapAt[key] = now

        if (selected) {
            if (now - last <= TAB_DOUBLE_TAP_REFRESH_WINDOW_MS) {
                triggerRefreshForSection(NavigationSection.fromRouteId(tab.route.section))
            }
            return
        }

        scrollChromeState.animateToTop()
        // Drive the pager directly; launchSingleTop nav to the same MainTabsRoute
        // yields no new entry, so observing a derived section would break paging.
        val targetIndex = NavigationSection.fromRouteId(tab.route.section).ordinal
        coroutineScope.launch {
            pagerState.animateScrollToPage(targetIndex)
        }
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(initialTab) {
        when (initialTab) {
            is OverviewRoute -> navController.navigate(MainTabsRoute(MAIN_TAB_OVERVIEW))
            is AppsRoute -> navController.navigate(MainTabsRoute(MAIN_TAB_APPS))
            is AppsManageRoute -> navController.navigate(AppsManageRoute)
            is InterceptRoute -> navController.navigate(InterceptRoute)
            is RecordsRoute -> navController.navigate(MainTabsRoute(MAIN_TAB_RECORDS))
            is AdvancedRoute -> navController.navigate(MainTabsRoute(MAIN_TAB_ADVANCED))
            is SettingsRoute -> navController.navigate(MainTabsRoute(MAIN_TAB_SETTINGS))
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
            .semantics { testTagsAsResourceId = true }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isCompact) {
                AppNavigationRail(
                    header = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    },
                    modifier = Modifier.fillMaxHeight(),
                    alwaysShowLabel = false,
                    items = tabs.mapIndexed { index, tab ->
                        AppNavigationItemSpec(
                            label = tab.label,
                            icon = tab.icon,
                            selected = index == selectedIndex,
                            onClick = { handleTabClick(tab, index == selectedIndex) },
                            testTag = benchmarkNavTag(index),
                        )
                    },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = compactBottomBarPadding),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = MainTabsRoute(),
                ) {
                    composable<MainTabsRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<MainTabsRoute>()
                        MainTabsPager(
                            selectedSection = NavigationSection.fromRouteId(route.section),
                            pagerState = pagerState,
                            appBlockRefreshTrigger = appBlockRefreshTrigger,
                            recordsRefreshTrigger = recordsRefreshTrigger,
                            appConfigViewModel = appConfigViewModel,
                            scrollChromeState = pageScrollChromeState,
                            onCheckUpdate = { settingsViewModel.requestPreferredUpdate() },
                            onNavigateToAppConfigDetail = { packageName, origin ->
                                navController.navigate(
                                    AppConfigDetailRoute(
                                        packageName = packageName,
                                        origin = origin,
                                    ),
                                )
                            },
                            onNavigateToVerificationSettings = {
                                navController.navigate(VerificationSettingsRoute)
                            },
                            onNavigateToIntercept = {
                                navController.navigate(InterceptRoute)
                            },
                            onNavigateToRelayConfig = { origin ->
                                navController.navigate(RelayConfigRoute(origin = origin))
                            },
                            onNavigateToForwardKeepAlive = {
                                navController.navigate(ForwardKeepAliveRoute)
                            },
                            onNavigateToScheduledReminder = {
                                navController.navigate(ScheduledReminderRoute)
                            },
                            onNavigateToRemoteAgent = {
                                navController.navigate(RemoteAgentRoute)
                            },
                            onNavigateToScheduledTasks = if (BuildConfig.ENABLE_SMS_CHANNEL) {
                                { navController.navigate(ScheduledTasksRoute) }
                            } else {
                                null
                            },
                            onNavigateToCloudBackup = { source, backupNow ->
                                navController.navigate(
                                    CloudBackupRoute(
                                        initialSource = source?.name,
                                        backupNow = backupNow,
                                    ),
                                )
                            },
                        )
                    }
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
                        )
                    }
                    composable<SmsCodeRuleEditorRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SmsCodeRuleEditorRoute>()
                        io.github.magisk317.relay.ui.smscoderule.SmsCodeRuleEditorScreen(
                            ruleId = route.id,
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

        AnimatedCompactBottomNavigationChrome(
            visible = compactBottomBarVisible,
            items = tabs.mapIndexed { index, tab ->
                AppNavigationItemSpec(
                    label = tab.label,
                    icon = tab.icon,
                    selected = index == selectedIndex,
                    onClick = { handleTabClick(tab, index == selectedIndex) },
                    testTag = benchmarkNavTag(index),
                )
            },
        )

        AnimatedSystemBarsScrim(
            visible = mainChromeVisible,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabsPager(
    selectedSection: NavigationSection,
    appBlockRefreshTrigger: Int,
    recordsRefreshTrigger: Int,
    appConfigViewModel: AppConfigViewModel,
    scrollChromeState: ScrollChromeState?,
    onCheckUpdate: () -> Unit,
    onNavigateToAppConfigDetail: (String, String) -> Unit,
    onNavigateToVerificationSettings: () -> Unit,
    onNavigateToIntercept: () -> Unit,
    onNavigateToRelayConfig: (String) -> Unit,
    onNavigateToForwardKeepAlive: () -> Unit,
    onNavigateToScheduledReminder: () -> Unit,
    onNavigateToRemoteAgent: () -> Unit,
    onNavigateToScheduledTasks: (() -> Unit)?,
    onNavigateToCloudBackup: (BackupSource?, Boolean) -> Unit,
    pagerState: PagerState,
) {
    LaunchedEffect(selectedSection) {
        if (pagerState.currentPage != selectedSection.ordinal) {
            pagerState.scrollToPage(selectedSection.ordinal)
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        beyondViewportPageCount = 0,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val section = NavigationSection.entries[page]
        val isActivePage = page == pagerState.currentPage || page == selectedSection.ordinal
        if (!isActivePage) {
            Box(modifier = Modifier.fillMaxSize())
            return@HorizontalPager
        }
        when (section) {
            NavigationSection.OVERVIEW -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(BENCHMARK_TAB_OVERVIEW),
                ) {
                    OverviewScreen(onCheckUpdate = onCheckUpdate)
                }
            }

            NavigationSection.APPS -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(BENCHMARK_TAB_APPS),
                ) {
                    AppConfigScreen(
                        onBack = null,
                        onAppClick = { app ->
                            onNavigateToAppConfigDetail(app.packageName, ROUTE_ORIGIN_APPS)
                        },
                        refreshTrigger = appBlockRefreshTrigger,
                        viewModel = appConfigViewModel,
                        scrollChromeState = scrollChromeState,
                    )
                }
            }

            NavigationSection.RECORDS -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(BENCHMARK_TAB_RECORDS),
                ) {
                    CodeRecordScreen(
                        onBack = null,
                        refreshTrigger = recordsRefreshTrigger,
                        scrollChromeState = scrollChromeState,
                    )
                }
            }

            NavigationSection.ADVANCED -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(BENCHMARK_TAB_ADVANCED),
                ) {
                    AdvancedScreen(
                        onInterceptClick = onNavigateToIntercept,
                        onVerificationConfigClick = onNavigateToVerificationSettings,
                        onRelayConfigClick = { onNavigateToRelayConfig(ROUTE_ORIGIN_ADVANCED) },
                        onForwardKeepAliveClick = onNavigateToForwardKeepAlive,
                        onScheduledReminderClick = onNavigateToScheduledReminder,
                        onRemoteAgentClick = onNavigateToRemoteAgent,
                        onNavigateToScheduledTasks = onNavigateToScheduledTasks,
                    )
                }
            }

            NavigationSection.SETTINGS -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(BENCHMARK_TAB_SETTINGS),
                ) {
                    SettingsHomeScreen(
                        onOpenVerification = onNavigateToVerificationSettings,
                        onOpenAdvancedRelay = { onNavigateToRelayConfig(ROUTE_ORIGIN_SETTINGS) },
                        onOpenCloudBackup = onNavigateToCloudBackup,
                    )
                }
            }
        }
    }
}

private fun parseBackupSource(rawSource: String): BackupSource? {
    return BackupSource.entries.firstOrNull { it.name == rawSource }
}

private fun benchmarkNavTag(index: Int): String {
    return when (NavigationSection.entries[index]) {
        NavigationSection.OVERVIEW -> BENCHMARK_NAV_OVERVIEW
        NavigationSection.APPS -> BENCHMARK_NAV_APPS
        NavigationSection.RECORDS -> BENCHMARK_NAV_RECORDS
        NavigationSection.ADVANCED -> BENCHMARK_NAV_ADVANCED
        NavigationSection.SETTINGS -> BENCHMARK_NAV_SETTINGS
    }
}
