package io.github.magisk317.relay.ui.setup

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The setup report renders through the shared kit onboarding primitives - the same contract
 * MiPush's wizard uses - so the two onboarding surfaces cannot drift apart row by row while
 * all copy stays owned by this app.
 */
class SetupScreenOnboardingContractTest {
    @Test
    fun doneStepRendersThroughTheSharedReportPrimitives() {
        val doneStep = readSource()
            .substringAfter("private fun DoneStep(")
            .substringBefore("private fun PermissionRow(")

        assertTrue(doneStep.contains("OnboardingSummaryHeader("), "summary header missing")
        assertTrue(doneStep.contains("OnboardingStatusRow("), "status row missing")
        assertTrue(doneStep.contains("OnboardingItemState.Granted"), "granted state missing")
        assertTrue(doneStep.contains("OnboardingItemState.Outstanding"), "outstanding state missing")
        assertFalse(doneStep.contains("appColor("), "the report must not hand-pick status colours")
        assertFalse(doneStep.contains("horizontalArrangement"), "the report must not hand-roll the row layout")
    }

    @Test
    fun actionRowsStayClickableAndKeepNeutralHintsWhenUnmet() {
        val row = readSource().substringAfter("private fun PermissionRow(")

        assertTrue(row.contains("OnboardingActionRowCard("), "action card missing")
        assertTrue(row.contains("onClick = onClick"), "click must route through AppCard's onClick slot")
        assertTrue(
            row.contains("state = if (granted) OnboardingItemState.Granted else null"),
            "an unmet row keeps the neutral action hint instead of an error tint",
        )
    }

    @Test
    fun reportCopyStaysOwnedByTheApp() {
        val source = readSource()

        listOf(
            "setup_done_title",
            "setup_done_granted",
            "setup_done_all_ready",
            "setup_done_missing",
            "setup_done_list_title",
            "setup_done_item_granted",
            "setup_done_item_missing",
        ).forEach { key ->
            assertTrue(source.contains("R.string.$key"), "setup screen must still use $key")
        }
    }

    private fun readSource(): String {
        val override = System.getProperty("xinyi.setupSourceRoot") ?: System.getenv("XINYI_SETUP_SOURCE_ROOT")
        val file = if (override.isNullOrBlank()) {
            File("src/main/kotlin/io/github/magisk317/relay/ui/setup/SetupScreen.kt")
        } else {
            File(override, "SetupScreen.kt")
        }
        check(file.isFile) { "source missing: ${file.absolutePath}" }
        return file.readText()
    }
}
