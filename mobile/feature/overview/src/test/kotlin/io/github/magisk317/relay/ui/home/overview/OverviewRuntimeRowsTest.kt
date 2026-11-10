package io.github.magisk317.relay.ui.home.overview

import io.github.magisk317.relay.feature.mode.ListenerRowState
import io.github.magisk317.relay.feature.mode.ShizukuRowState
import io.github.magisk317.relay.feature.mode.XposedRowState
import io.github.magisk317.relay.mobile.core.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverviewRuntimeRowsTest {
    @Test
    fun `rows map runtime states to separate localized labels`() {
        assertEquals(R.string.overview_runtime_active, XposedRowState.ACTIVE.labelRes())
        assertEquals(R.string.status_not_active, XposedRowState.INACTIVE.labelRes())
        assertEquals(R.string.overview_runtime_connected, ListenerRowState.CONNECTED.labelRes())
        assertEquals(R.string.overview_runtime_disconnected, ListenerRowState.DISCONNECTED.labelRes())
        assertEquals(R.string.overview_runtime_authorized, ShizukuRowState.AUTHORIZED.labelRes())
        assertEquals(R.string.overview_runtime_unauthorized, ShizukuRowState.UNAUTHORIZED.labelRes())
        assertEquals(R.string.overview_runtime_disconnected, ShizukuRowState.UNREACHABLE.labelRes())
    }
}
