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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.magisk317.xposed.backup.BackupSource
import io.github.magisk317.relay.mobile.core.R
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
import io.github.magisk317.relay.ui.home.settings.DeviceControlScreen
import io.github.magisk317.relay.ui.home.settings.DiagnosticsScreen
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
import io.github.magisk317.relay.ui.nav.DeviceControlRoute
import io.github.magisk317.relay.ui.nav.DiagnosticsRoute
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
import io.github.magisk317.relay.common.utils.RelayBindPayload
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
import io.github.magisk317.relay.ui.nav.ThemeSettingsRoute
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
import io.github.magisk317.uikit.surface.AppIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.NavHostController
import io.github.magisk317.uikit.scroll.ScrollChromeState


internal fun parseBackupSource(rawSource: String): BackupSource? {
    return BackupSource.entries.firstOrNull { it.name == rawSource }
}

@Composable
internal fun MainScreenSecondaryNavHost(
    navController: NavHostController,
    pageScrollChromeState: ScrollChromeState?,
    appConfigViewModel: AppConfigViewModel,
    appBlockRefreshTrigger: Int,
    recordsRefreshTrigger: Int,
    interceptRefreshTrigger: Int,
    contentBottomPadding: Dp,
) {
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
        composable<RemoteAgentRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<RemoteAgentRoute>()
            RemoteAgentScreen(
                onBack = { navController.popBackStack() },
                bindRequest = if (route.bindCode.isBlank()) {
                    null
                } else {
                    RelayBindPayload(bindCode = route.bindCode, baseUrl = route.baseUrl)
                },
            )
        }
        composable<DeviceControlRoute> {
            DeviceControlScreen(onBack = { navController.popBackStack() })
        }
        composable<DiagnosticsRoute> {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
        composable<ThemeSettingsRoute> {
            io.github.magisk317.relay.ui.home.settings.ThemeSettingsPage(
                onBack = { navController.popBackStack() },
            )
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

