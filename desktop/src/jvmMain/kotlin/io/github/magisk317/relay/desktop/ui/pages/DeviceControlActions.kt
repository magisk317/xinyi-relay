package io.github.magisk317.relay.desktop.ui.pages

data class DesktopDeviceControlAction(
    val labelKey: String,
    val action: String,
    val params: Map<String, String>,
)

val DESKTOP_DEVICE_CONTROL_ACTIONS = listOf(
    DesktopDeviceControlAction("deviceControl.action.ring", "SET_RINGER_MODE", mapOf("mode" to "normal")),
    DesktopDeviceControlAction("deviceControl.action.silent", "SET_RINGER_MODE", mapOf("mode" to "silent")),
    DesktopDeviceControlAction("deviceControl.action.vibrate", "SET_RINGER_MODE", mapOf("mode" to "vibrate")),
    DesktopDeviceControlAction("deviceControl.action.dnd", "SET_INTERRUPTION_FILTER", mapOf("filter" to "priority")),
    DesktopDeviceControlAction("deviceControl.action.allowAll", "SET_INTERRUPTION_FILTER", mapOf("filter" to "all")),
)
