package io.github.magisk317.relay.ui.home.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun rememberSharedSettingsViewModel(): SettingsViewModel {
    val activityOwner = LocalActivity.current as? ComponentActivity
    return if (activityOwner != null) {
        koinViewModel<SettingsViewModel>(viewModelStoreOwner = activityOwner)
    } else {
        koinViewModel()
    }
}
