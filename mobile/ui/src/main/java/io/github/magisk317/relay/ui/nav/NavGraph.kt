package io.github.magisk317.relay.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.magisk317.relay.ui.home.MainScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import kotlinx.serialization.Serializable

const val ROUTE_ORIGIN_ADVANCED = "advanced"
const val ROUTE_ORIGIN_SETTINGS = "settings"
const val ROUTE_ORIGIN_APPS = "apps"

@Serializable
object MainRoute

@Serializable
object OverviewRoute

@Serializable
object SettingsGraphRoute

@Serializable
object SettingsRoute

@Serializable
object FaqRoute

@Serializable
object InterceptRoute

@Serializable
object RecordsGraphRoute

@Serializable
object RecordsRoute

@Serializable
data class ScopedRecordsRoute(val origin: String = ROUTE_ORIGIN_ADVANCED)

@Serializable
object AppsRoute

@Serializable
object AppGraphRoute

@Serializable
data class SendersRoute(val origin: String = ROUTE_ORIGIN_ADVANCED)

@Serializable
object AdvancedGraphRoute

@Serializable
object AdvancedRoute

@Serializable
object ScheduledTasksRoute

@Serializable
data class ScheduledTaskConfigRoute(val id: Long = 0L)

@Serializable
object ScheduledReminderRoute

@Serializable
object ForwardKeepAliveRoute

@Serializable
object VerificationSettingsRoute

@Serializable
object RemoteAgentRoute

@Serializable
data class RelayConfigRoute(val origin: String = ROUTE_ORIGIN_ADVANCED)

@Serializable
data class GlobalForwardFilterRoute(val origin: String = ROUTE_ORIGIN_ADVANCED)

@Serializable
data class SenderConfigRoute(
    val id: Long,
    val type: Int,
    val origin: String = ROUTE_ORIGIN_ADVANCED,
)

@Serializable
data class RulesRoute(
    val senderId: Long = 0,
    val origin: String = ROUTE_ORIGIN_SETTINGS,
)

@Serializable
data class RuleConfigRoute(
    val id: Long = 0,
    val origin: String = ROUTE_ORIGIN_SETTINGS,
)

@Serializable
data class SmsCodeRulesRoute(
    val origin: String = ROUTE_ORIGIN_SETTINGS,
)

@Serializable
data class SmsCodeRuleEditorRoute(
    val id: Long = 0,
    val origin: String = ROUTE_ORIGIN_SETTINGS,
)

@Serializable
data class AppRoutingRoute(val origin: String = ROUTE_ORIGIN_ADVANCED)

@Serializable
object AppsManageRoute

@Serializable
data class AppConfigDetailRoute(
    val packageName: String,
    val origin: String = ROUTE_ORIGIN_APPS,
)

@Serializable
data class AppNotifySenderBindingRoute(
    val packageName: String,
    val origin: String = ROUTE_ORIGIN_APPS,
)

@Serializable
data class AppForwardFilterRoute(
    val packageName: String,
    val origin: String = ROUTE_ORIGIN_APPS,
)

@Serializable
data class SenderForwardFilterRoute(
    val senderId: Long,
    val origin: String = ROUTE_ORIGIN_ADVANCED,
)

@Serializable
data class CloudBackupRoute(
    val initialSource: String? = null,
    val backupNow: Boolean = false,
)

@Composable
fun SmsCodeNavHost(
    navController: NavHostController,
    onBack: () -> Unit,
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
) {
    NavHost(
        navController = navController,
        startDestination = MainRoute,
        modifier = modifier,
    ) {
        composable<MainRoute> {
            MainScreen(
                initialTab = initialTab,
                onInitialTabConsumed = onInitialTabConsumed,
                hazeState = hazeState,
                hazeStyle = hazeStyle,
            )
        }
    }
}
