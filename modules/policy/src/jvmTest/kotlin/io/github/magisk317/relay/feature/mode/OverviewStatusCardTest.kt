package io.github.magisk317.relay.feature.mode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverviewStatusCardTest {
    @Test
    fun `framework takes priority for every shizuku combination`() {
        val framework = FrameworkIdentity("LSPosed", "1.0")
        for (build in listOf(false, true)) for (online in listOf(false, true)) for (authorized in listOf(false, true)) {
            assertEquals(StatusCardState.Framework(framework), overviewStatusCard(true, framework, build, online, authorized, "13"))
        }
    }

    @Test
    fun `shizuku requires enabled build online and authorization`() {
        val stale = FrameworkIdentity("installed-only", "old")
        for (build in listOf(false, true)) for (online in listOf(false, true)) for (authorized in listOf(false, true)) {
            val expected = if (build && online && authorized) StatusCardState.Shizuku("13") else StatusCardState.Inactive
            assertEquals(expected, overviewStatusCard(false, stale, build, online, authorized, "13"))
        }
    }

    @Test
    fun `missing versions are kept absent rather than borrowing other runtime metadata`() {
        val identity = FrameworkIdentity(null, null)
        assertEquals(StatusCardState.Framework(identity), overviewStatusCard(true, identity, true, true, true, "13"))
        assertEquals(StatusCardState.Shizuku(null), overviewStatusCard(false, identity, true, true, true, null))
    }
}
