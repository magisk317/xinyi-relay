package io.github.magisk317.relay.xp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HookTargetDiagnosticsTest {

    @Test
    fun describePackageProbe_marksExactPhoneTarget() {
        val result = HookTargetDiagnostics.describePackageProbe(
            packageName = "com.android.phone",
            processName = "com.android.phone",
        )

        assertEquals(listOf("sms_handler,sms_forward"), result.matchedTargets)
        assertTrue(result.candidateReasons.contains("phone_token"))
        assertEquals(null, result.missReason)
    }

    @Test
    fun describePackageProbe_marksTelephonyProviderTarget() {
        val result = HookTargetDiagnostics.describePackageProbe(
            packageName = "com.android.providers.telephony",
            processName = "com.android.providers.telephony",
        )

        assertEquals(listOf("sms_provider"), result.matchedTargets)
        assertTrue(result.candidateReasons.contains("telephony_token"))
        assertTrue(result.candidateReasons.contains("provider_token"))
    }

    @Test
    fun describePackageProbe_reportsCandidateOnlyMiss() {
        val result = HookTargetDiagnostics.describePackageProbe(
            packageName = "android",
            processName = "android",
        )

        assertTrue(result.matchedTargets.isEmpty())
        assertTrue(result.candidateReasons.contains("system_server_candidate"))
        assertEquals("candidate_only", result.missReason)
    }

    @Test
    fun describePackageProbe_ignoresOrdinaryApp() {
        val result = HookTargetDiagnostics.describePackageProbe(
            packageName = "com.example.app",
            processName = "com.example.app",
        )

        assertTrue(result.candidateReasons.isEmpty())
        assertTrue(result.matchedTargets.isEmpty())
        assertEquals(false, result.isCandidate)
    }
}
