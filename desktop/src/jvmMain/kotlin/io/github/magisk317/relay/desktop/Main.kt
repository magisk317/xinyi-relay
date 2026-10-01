package io.github.magisk317.relay.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.magisk317.relay.contract.constant.RelayPrefConst

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Xinyi Relay Desktop",
    ) {
        MaterialTheme {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Xinyi Relay Desktop — KMP skeleton")
                // prove the jvm variant of the shared contract is consumable
                Text("pref key: ${RelayPrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED}")
            }
        }
    }
}
