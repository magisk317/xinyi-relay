package io.github.magisk317.relay.feature.mode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OverviewRowsPropertyTest {
    @Test
    fun `all signal combinations map independently to rows`() {
        val flags = listOf(false, true)
        for (build in flags) for (xposed in flags) for (listener in flags) for (online in flags) for (authorized in flags) {
            val rows = overviewRows(build, xposed, listener, online, authorized)
            assertEquals(if (xposed) XposedRowState.ACTIVE else XposedRowState.INACTIVE, rows.xposed)
            assertEquals(if (!build) null else if (listener) ListenerRowState.CONNECTED else ListenerRowState.DISCONNECTED, rows.listener)
            assertEquals(if (!build) null else if (!online) ShizukuRowState.UNREACHABLE else if (authorized) ShizukuRowState.AUTHORIZED else ShizukuRowState.UNAUTHORIZED, rows.shizuku)
        }
    }

    @Test
    fun `nonroot shizuku cannot make the framework row active`() {
        assertEquals(XposedRowState.INACTIVE, overviewRows(true, false, true, true, true).xposed)
        assertNull(overviewRows(false, true, true, true, true).shizuku)
    }
}
