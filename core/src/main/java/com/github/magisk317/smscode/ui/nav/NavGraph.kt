package com.github.magisk317.smscode.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.github.magisk317.smscode.ui.home.MainScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import kotlinx.serialization.Serializable

@Serializable
object MainRoute

@Serializable
object OverviewRoute

@Serializable
object SettingsRoute

@Serializable
object FaqRoute

@Serializable
object InterceptRoute

@Serializable
object RecordsRoute

@Serializable
object AppBlockRoute

@Serializable
object SendersRoute

@Serializable
object AdvancedRoute

@Serializable
object WebUiConfigRoute

@Serializable
object GlobalForwardFilterRoute

@Serializable
data class SenderConfigRoute(val id: Long, val type: Int)

@Serializable
data class RulesRoute(val senderId: Long = 0)

@Serializable
data class RuleConfigRoute(val id: Long = 0)

@Serializable
object NotificationRulesRoute

@Serializable
object AppConfigRoute

@Serializable
data class AppConfigDetailRoute(val packageName: String)

@Serializable
data class AppNotifySenderBindingRoute(val packageName: String)

@Serializable
data class SenderNotifyScopeRoute(val senderId: Long)

@Serializable
data class AppForwardFilterRoute(val packageName: String)

@Serializable
data class SenderForwardFilterRoute(val senderId: Long)

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
