package io.github.magisk317.relay.desktop.ui.pages

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeviceControlActionsTest {

    @Test
    fun `desktop exposes the same five one shot actions as the web console`() {
        assertEquals(
            listOf(
                "SET_RINGER_MODE" to mapOf("mode" to "normal"),
                "SET_RINGER_MODE" to mapOf("mode" to "silent"),
                "SET_RINGER_MODE" to mapOf("mode" to "vibrate"),
                "SET_INTERRUPTION_FILTER" to mapOf("filter" to "priority"),
                "SET_INTERRUPTION_FILTER" to mapOf("filter" to "all"),
            ),
            DESKTOP_DEVICE_CONTROL_ACTIONS.map { it.action to it.params },
        )
    }
}
