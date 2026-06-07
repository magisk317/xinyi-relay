package io.github.magisk317.relay.ui.home.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun rememberSharedSettingsViewModel(): SettingsViewModel {
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    return if (activityOwner != null) {
        koinViewModel<SettingsViewModel>(viewModelStoreOwner = activityOwner)
    } else {
        koinViewModel()
    }
}
