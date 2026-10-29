package io.github.magisk317.relay.domain.diagnostics

import io.github.magisk317.smscode.runtime.contract.diagnostics.HookProcessStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticsSupportTest {

    @Test
    fun `hook matrix marks heartbeats after boot`() {
        val rows = HookProcessMatrix.build(
            processes = listOf(
                HookProcessStatus("com.android.phone", "com.android.phone", 300L, "sms", 55),
                HookProcessStatus("android", "system", 100L, "process_online", 55),
            ),
            bootStartAtMs = 200L,
        )

        assertEquals("com.android.phone", rows.first().packageName)
        assertTrue(rows.first().thisBoot)
        assertFalse(rows[1].thisBoot)
    }

    @Test
    fun `system row is detected by package or process`() {
        val byPackage = HookProcessMatrix.build(
            listOf(HookProcessStatus("android", "android", 300L)),
            bootStartAtMs = 0L,
        )
        val byProcess = HookProcessMatrix.build(
            listOf(HookProcessStatus("android", "system", 300L)),
            bootStartAtMs = 0L,
        )

        assertTrue(HookProcessMatrix.hasSystemHeartbeatThisBoot(byPackage))
        assertTrue(HookProcessMatrix.hasSystemHeartbeatThisBoot(byProcess))
        assertFalse(HookProcessMatrix.hasSystemHeartbeatThisBoot(emptyList()))
    }

    @Test
    fun `reboot verdict prefers the scope gap over a reboot`() {
        val result = RebootVerdict.evaluate(
            bootStartAtMs = 100L,
            firstInstallTimeMs = 200L,
            lastUpdateTimeMs = 300L,
            systemScopeSelected = false,
            systemProcessSeenThisBoot = false,
        )

        assertEquals(RebootVerdict.Reason.SYSTEM_SCOPE_MISSING, result.reason)
        assertFalse(result.needsReboot)
    }

    @Test
    fun `reboot verdict reports an install after boot`() {
        val result = RebootVerdict.evaluate(
            bootStartAtMs = 100L,
            firstInstallTimeMs = 200L,
            lastUpdateTimeMs = 300L,
            systemScopeSelected = true,
            systemProcessSeenThisBoot = false,
        )

        assertEquals(RebootVerdict.Reason.INSTALLED_AFTER_BOOT, result.reason)
        assertTrue(result.needsReboot)
    }

    @Test
    fun `reboot verdict reports a missing system heartbeat after boot`() {
        val result = RebootVerdict.evaluate(
            bootStartAtMs = 100L,
            firstInstallTimeMs = 10L,
            lastUpdateTimeMs = 20L,
            systemScopeSelected = true,
            systemProcessSeenThisBoot = false,
        )

        assertEquals(RebootVerdict.Reason.SYSTEM_NOT_LOADED, result.reason)
        assertTrue(result.needsReboot)
    }

    @Test
    fun `reboot verdict is clean when system reported this boot`() {
        val result = RebootVerdict.evaluate(
            bootStartAtMs = 100L,
            firstInstallTimeMs = 200L,
            lastUpdateTimeMs = 300L,
            systemScopeSelected = true,
            systemProcessSeenThisBoot = true,
        )

        assertEquals(RebootVerdict.Reason.OK, result.reason)
        assertFalse(result.needsReboot)
    }

    @Test
    fun `scope recommendation keeps installed required and optional packages`() {
        val recommended = ScopeRecommender.recommend(
            installedPackages = setOf(
                "com.android.phone",
                "com.android.providers.telephony",
                "com.android.mms",
                "com.xiaomi.phone",
                "com.android.contacts",
            ),
        )

        assertEquals(
            listOf(
                "android",
                "system",
                "com.android.phone",
                "com.android.providers.telephony",
                "com.android.mms",
                "com.xiaomi.phone",
            ),
            recommended,
        )
    }

    @Test
    fun `scope recommendation drops optional packages that are not installed`() {
        val recommended = ScopeRecommender.recommend(
            installedPackages = setOf("com.android.phone"),
        )

        assertFalse("com.xiaomi.phone" in recommended)
        assertFalse("com.android.mms" in recommended)
        assertTrue("system" in recommended)
    }

    @Test
    fun `scope missing keeps order and drops duplicates`() {
        assertEquals(
            listOf("system", "com.android.mms"),
            ScopeRecommender.missing(
                granted = listOf("android", "com.android.phone"),
                wanted = listOf("android", "system", "com.android.phone", "com.android.mms", "system"),
            ),
        )
    }
}
