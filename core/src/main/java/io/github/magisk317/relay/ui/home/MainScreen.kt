package io.github.magisk317.relay.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.nav.*
import io.github.magisk317.relay.ui.record.CodeRecordScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import android.os.SystemClock
import org.koin.compose.viewmodel.koinViewModel

@Immutable
data class TabItem<T : Any>(val label: String, val icon: ImageVector, val route: T)

private const val TAB_DOUBLE_TAP_REFRESH_WINDOW_MS = 350L
private val COMPACT_BOTTOM_BAR_CONTENT_PADDING = 80.dp

private enum class NavigationSection {
    OVERVIEW,
    APPS,
    RECORDS,
    ADVANCED,
    SETTINGS,
}

@Composable
@Suppress("CyclomaticComplexMethod")
fun MainScreen(
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
) {
    val navController = rememberNavController()
    val appConfigViewModel: AppConfigViewModel = koinViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = listOf(
        TabItem(stringResource(R.string.tab_overview), Icons.Default.Home, OverviewRoute),
        TabItem(stringResource(R.string.tab_blacklist), Icons.AutoMirrored.Filled.List, AppsRoute),
        TabItem(stringResource(R.string.tab_records), Icons.Default.DateRange, RecordsRoute),
        TabItem(stringResource(R.string.tab_advanced), Icons.Default.Build, AdvancedRoute),
        TabItem(stringResource(R.string.tab_settings), Icons.Default.Settings, SettingsRoute),
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
            destination.hasRoute(InterceptRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(WebUiConfigRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(ScheduledReminderRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(ForwardKeepAliveRoute::class) -> NavigationSection.ADVANCED
            destination.hasRoute(RelayConfigRoute::class) ->
                sectionFromOrigin(entry.toRoute<RelayConfigRoute>().origin)
            destination.hasRoute(SendersRoute::class) ->
                sectionFromOrigin(entry.toRoute<SendersRoute>().origin)
            destination.hasRoute(SenderConfigRoute::class) ->
                sectionFromOrigin(entry.toRoute<SenderConfigRoute>().origin)
            destination.hasRoute(SenderNotifyScopeRoute::class) ->
                sectionFromOrigin(entry.toRoute<SenderNotifyScopeRoute>().origin)
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

    fun resolveExactTopLevelIndex(destination: NavDestination?): Int? {
        if (destination == null) return null
        return when {
            destination.hasRoute(OverviewRoute::class) -> 0
            destination.hasRoute(AppsRoute::class) -> 1
            destination.hasRoute(RecordsRoute::class) -> 2
            destination.hasRoute(AdvancedRoute::class) -> 3
            destination.hasRoute(SettingsRoute::class) -> 4
            else -> null
        }
    }

    fun resolveTransitionDirection(initial: NavBackStackEntry?, target: NavBackStackEntry?): Int {
        val initialIndex = resolveTabIndex(initial)
        val targetIndex = resolveTabIndex(target)
        return if (targetIndex >= initialIndex) 1 else -1
    }

    fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
        if (destination == null) return true
        return destination.hasRoute(OverviewRoute::class) ||
            destination.hasRoute(AppsRoute::class) ||
            destination.hasRoute(RecordsRoute::class) ||
            destination.hasRoute(AdvancedRoute::class) ||
            destination.hasRoute(SettingsRoute::class)
    }

    val selectedIndex = resolveTabIndex(navBackStackEntry)

    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600
    val compactBottomBarPadding: Dp = if (isCompact && shouldShowCompactBottomBar(currentDestination)) {
        COMPACT_BOTTOM_BAR_CONTENT_PADDING +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }
    var appBlockRefreshTrigger by remember { mutableIntStateOf(0) }
    var recordsRefreshTrigger by remember { mutableIntStateOf(0) }
    var interceptRefreshTrigger by remember { mutableIntStateOf(0) }
    var settingsRefreshTrigger by remember { mutableIntStateOf(0) }
    val tabLastTapAt = remember { mutableStateMapOf<String, Long>() }

    fun triggerRefreshForTab(route: Any) {
        when (route) {
            is AppsRoute -> appBlockRefreshTrigger++
            is RecordsRoute -> recordsRefreshTrigger++
            is InterceptRoute -> interceptRefreshTrigger++
            is AppsManageRoute -> appBlockRefreshTrigger++
            is SettingsRoute -> settingsRefreshTrigger++
            else -> Unit
        }
    }

    fun handleTabClick(tab: TabItem<*>, selected: Boolean) {
        val key = tab.route::class.qualifiedName ?: tab.label
        val now = SystemClock.elapsedRealtime()
        val last = tabLastTapAt[key] ?: 0L
        tabLastTapAt[key] = now

        if (selected) {
            if (now - last <= TAB_DOUBLE_TAP_REFRESH_WINDOW_MS) {
                triggerRefreshForTab(tab.route)
            }
            return
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
            is OverviewRoute -> navController.navigate(OverviewRoute)
            is AppsRoute -> navController.navigate(AppsRoute)
            is AppsManageRoute -> navController.navigate(AppsManageRoute)
            is InterceptRoute -> navController.navigate(InterceptRoute)
            is RecordsRoute -> navController.navigate(RecordsRoute)
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
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isCompact) {
                NavigationRail(
                    header = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    },
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    tabs.forEach { tab ->
                        val selected = tabs.indexOf(tab) == selectedIndex
                        NavigationRailItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = selected,
                            alwaysShowLabel = false,
                            onClick = { handleTabClick(tab, selected) },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = compactBottomBarPadding),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = OverviewRoute,
                    enterTransition = {
                        val targetTopLevelIndex = resolveExactTopLevelIndex(targetState.destination)
                        if (targetTopLevelIndex != null) {
                            val direction = resolveTransitionDirection(
                                initial = initialState,
                                target = targetState,
                            )
                            slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { fullWidth -> direction * fullWidth },
                            ) + fadeIn(animationSpec = tween(300))
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { fullWidth -> fullWidth },
                            ) + fadeIn(animationSpec = tween(300))
                        }
                    },
                    exitTransition = {
                        val targetTopLevelIndex = resolveExactTopLevelIndex(targetState.destination)
                        if (targetTopLevelIndex != null) {
                            val direction = resolveTransitionDirection(
                                initial = initialState,
                                target = targetState,
                            )
                            slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { fullWidth -> -direction * fullWidth },
                            ) + fadeOut(animationSpec = tween(300))
                        } else {
                            slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { fullWidth -> -fullWidth },
                            ) + fadeOut(animationSpec = tween(300))
                        }
                    },
                    popEnterTransition = {
                        val initialTopLevelIndex = resolveExactTopLevelIndex(initialState.destination)
                        val targetTopLevelIndex = resolveExactTopLevelIndex(targetState.destination)
                        if (initialTopLevelIndex != null && targetTopLevelIndex != null) {
                            val direction = resolveTransitionDirection(
                                initial = initialState,
                                target = targetState,
                            )
                            slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { fullWidth -> -direction * fullWidth },
                            ) + fadeIn(animationSpec = tween(300))
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { fullWidth -> -fullWidth },
                            ) + fadeIn(animationSpec = tween(300))
                        }
                    },
                    popExitTransition = {
                        val initialTopLevelIndex = resolveExactTopLevelIndex(initialState.destination)
                        val targetTopLevelIndex = resolveExactTopLevelIndex(targetState.destination)
                        if (initialTopLevelIndex != null && targetTopLevelIndex != null) {
                            val direction = resolveTransitionDirection(
                                initial = initialState,
                                target = targetState,
                            )
                            slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { fullWidth -> direction * fullWidth },
                            ) + fadeOut(animationSpec = tween(300))
                        } else {
                            slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { fullWidth -> fullWidth },
                            ) + fadeOut(animationSpec = tween(300))
                        }
                    },
                ) {
                    composable<OverviewRoute> {
                        OverviewScreen(hazeState = hazeState, hazeStyle = hazeStyle)
                    }
                    navigation<AppGraphRoute>(startDestination = AppsRoute) {
                        composable<AppsRoute> {
                            AppConfigScreen(
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
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
                            )
                        }
                        composable<AppsManageRoute> {
                            AppConfigScreen(
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
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
                    }
                    composable<InterceptRoute> {
                        InterceptScreen(
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
                            refreshTrigger = interceptRefreshTrigger,
                        )
                    }
                    navigation<RecordsGraphRoute>(startDestination = RecordsRoute) {
                        composable<RecordsRoute> {
                            CodeRecordScreen(
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                onBack = null,
                                refreshTrigger = recordsRefreshTrigger,
                            )
                        }
                        composable<ScopedRecordsRoute> {
                            CodeRecordScreen(
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                onBack = { navController.popBackStack() },
                                refreshTrigger = recordsRefreshTrigger,
                            )
                        }
                    }
                    navigation<AdvancedGraphRoute>(startDestination = AdvancedRoute) {
                        composable<AdvancedRoute> {
                            AdvancedScreen(
                                onInterceptClick = { navController.navigate(InterceptRoute) },
                                onRelayConfigClick = {
                                    navController.navigate(RelayConfigRoute(origin = ROUTE_ORIGIN_ADVANCED))
                                },
                                onForwardKeepAliveClick = { navController.navigate(ForwardKeepAliveRoute) },
                                onScheduledReminderClick = { navController.navigate(ScheduledReminderRoute) },
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
                    }
                    composable<WebUiConfigRoute> {
                        WebUiConfigScreen(onBack = { navController.popBackStack() })
                    }
                    composable<SendersRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SendersRoute>()
                        val reopenTypeDialog by backStackEntry.savedStateHandle
                            .getStateFlow("reopen_type_dialog", false)
                            .collectAsStateWithLifecycle()
                        io.github.magisk317.relay.ui.sender.SenderListScreen(
                            onAddClick = { type ->
                                navController.navigate(
                                    SenderConfigRoute(
                                        id = 0L,
                                        type = type,
                                        origin = route.origin,
                                    ),
                                )
                            },
                            onEditClick = { id ->
                                navController.navigate(
                                    SenderConfigRoute(
                                        id = id,
                                        type = 1,
                                        origin = route.origin,
                                    ),
                                )
                            },
                            forceShowTypeDialog = reopenTypeDialog,
                            onForceShowHandled = {
                                backStackEntry.savedStateHandle["reopen_type_dialog"] = false
                            }
                        )
                    }
                    composable<AppRoutingRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<AppRoutingRoute>()
                        AppConfigScreen(
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
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
                        )
                    }
                    composable<SenderConfigRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SenderConfigRoute>()
                        io.github.magisk317.relay.ui.sender.SenderConfigScreen(
                            senderId = route.id,
                            senderTypeArg = route.type,
                            onOpenSenderNotifyScope = { senderId ->
                                navController.navigate(
                                    SenderNotifyScopeRoute(
                                        senderId = senderId,
                                        origin = route.origin,
                                    ),
                                )
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
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("reopen_type_dialog", true)
                                }
                                navController.popBackStack()
                            }
                        )
                    }
                    composable<SenderNotifyScopeRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SenderNotifyScopeRoute>()
                        io.github.magisk317.relay.ui.sender.SenderNotifyScopeScreen(
                            senderId = route.senderId,
                            onBack = { navController.popBackStack() },
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
                    navigation<SettingsGraphRoute>(startDestination = SettingsRoute) {
                        composable<SettingsRoute> {
                            SettingsHomeScreen(
                                onOpenVerification = { navController.navigate(VerificationSettingsRoute) },
                                onOpenAdvancedRelay = {
                                    navController.navigate(RelayConfigRoute(origin = ROUTE_ORIGIN_SETTINGS))
                                },
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
                    }
                }
            }
        }

        if (isCompact && shouldShowCompactBottomBar(currentDestination)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    },
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = tabs.indexOf(tab) == selectedIndex
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            ),
                            alwaysShowLabel = false,
                            onClick = { handleTabClick(tab, selected) },
                        )
                    }
                }
            }
        }
    }
}
