package io.github.magisk317.relay.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.magisk317.relay.desktop.ui.DesktopApp
import io.github.magisk317.relay.desktop.ui.XinyiDesktopTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Xinyi Relay Desktop",
    ) {
        window.minimumSize = java.awt.Dimension(960, 640)
        XinyiDesktopTheme {
            DesktopApp()
        }
    }
}
