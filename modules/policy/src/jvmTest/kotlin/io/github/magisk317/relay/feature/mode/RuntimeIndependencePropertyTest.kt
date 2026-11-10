package io.github.magisk317.relay.feature.mode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuntimeIndependencePropertyTest {
    @Test
    fun `changing nonroot and shizuku signals cannot change the xposed row`() {
        for (xposed in listOf(false, true)) {
            val expected = if (xposed) XposedRowState.ACTIVE else XposedRowState.INACTIVE
            for (enabled in listOf(false, true)) for (listener in listOf(false, true)) for (online in listOf(false, true)) {
                assertEquals(expected, overviewRows(enabled, xposed, listener, online, true).xposed)
                assertEquals(enabled && listener, NonRootRuntimeRule.isRunning(enabled, listener))
            }
        }
    }

    @Test
    fun `nonroot availability stays independent of hook activity`() {
        for (enabled in listOf(false, true)) for (listener in listOf(false, true)) {
            for (xposed in listOf(false, true)) {
                val rows = overviewRows(enabled, xposed, listener, false, false)
                assertEquals(if (!enabled) null else if (listener) ListenerRowState.CONNECTED else ListenerRowState.DISCONNECTED, rows.listener)
            }
        }
    }
}
