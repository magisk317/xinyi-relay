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


// Navigation section mapping and benchmark tags for the main shell.
// Pure route->section logic with no composable state.

internal enum class NavigationSection {
    OVERVIEW,
    APPS,
    RECORDS,
    ADVANCED,
    SETTINGS,
}

internal const val BENCHMARK_TAB_OVERVIEW = "xinyi_benchmark_tab_overview"
internal const val BENCHMARK_TAB_APPS = "xinyi_benchmark_tab_apps"
internal const val BENCHMARK_TAB_RECORDS = "xinyi_benchmark_tab_records"
internal const val BENCHMARK_TAB_ADVANCED = "xinyi_benchmark_tab_advanced"
internal const val BENCHMARK_TAB_SETTINGS = "xinyi_benchmark_tab_settings"
internal const val BENCHMARK_RECORDS_SWIPE_ZONE = "xinyi_benchmark_records_swipe_zone"
internal const val BENCHMARK_NAV_OVERVIEW = "xinyi_benchmark_nav_overview"
internal const val BENCHMARK_NAV_APPS = "xinyi_benchmark_nav_apps"
internal const val BENCHMARK_NAV_RECORDS = "xinyi_benchmark_nav_records"
internal const val BENCHMARK_NAV_ADVANCED = "xinyi_benchmark_nav_advanced"
internal const val BENCHMARK_NAV_SETTINGS = "xinyi_benchmark_nav_settings"

internal fun sectionFromOrigin(origin: String): NavigationSection {
    return when (origin) {
        ROUTE_ORIGIN_SETTINGS -> NavigationSection.SETTINGS
        ROUTE_ORIGIN_APPS -> NavigationSection.APPS
        else -> NavigationSection.ADVANCED
    }
}

internal fun resolveSection(entry: NavBackStackEntry?): NavigationSection {
    val destination = entry?.destination ?: return NavigationSection.OVERVIEW
    return when {
        destination.hasRoute(OverviewRoute::class) -> NavigationSection.OVERVIEW
        destination.hasRoute(AppsRoute::class) -> NavigationSection.APPS
        destination.hasRoute(AppsManageRoute::class) -> NavigationSection.APPS
        destination.hasRoute(RecordsRoute::class) -> NavigationSection.RECORDS
        destination.hasRoute(AdvancedRoute::class) -> NavigationSection.ADVANCED
        destination.hasRoute(SettingsRoute::class) -> NavigationSection.SETTINGS
        destination.hasRoute(ThemeSettingsRoute::class) -> NavigationSection.SETTINGS
        destination.hasRoute(VerificationSettingsRoute::class) -> NavigationSection.SETTINGS
        destination.hasRoute(RemoteAgentRoute::class) -> NavigationSection.SETTINGS
        destination.hasRoute(DeviceControlRoute::class) -> NavigationSection.SETTINGS
        destination.hasRoute(DiagnosticsRoute::class) -> NavigationSection.SETTINGS
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

internal fun resolveTabIndex(entry: NavBackStackEntry?): Int {
    return when (resolveSection(entry)) {
        NavigationSection.OVERVIEW -> 0
        NavigationSection.APPS -> 1
        NavigationSection.RECORDS -> 2
        NavigationSection.ADVANCED -> 3
        NavigationSection.SETTINGS -> 4
    }
}

internal fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
    if (destination == null) return true
    return destination.hasRoute(OverviewRoute::class) ||
        destination.hasRoute(AppsRoute::class) ||
        destination.hasRoute(RecordsRoute::class) ||
        destination.hasRoute(AdvancedRoute::class) ||
        destination.hasRoute(SettingsRoute::class)
}

internal fun shouldAllowScrollChrome(entry: NavBackStackEntry?): Boolean {
    val destination = entry?.destination ?: return false
    return destination.hasRoute(AppsRoute::class) ||
        destination.hasRoute(RecordsRoute::class) ||
        destination.hasRoute(AppsManageRoute::class) ||
        destination.hasRoute(AppRoutingRoute::class) ||
        destination.hasRoute(ScopedRecordsRoute::class)
}
internal fun benchmarkModifier(enabled: Boolean, tag: String): Modifier =
    Modifier.then(if (enabled) Modifier.testTag(tag) else Modifier)

