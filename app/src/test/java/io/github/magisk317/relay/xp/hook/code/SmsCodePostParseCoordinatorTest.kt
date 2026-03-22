package io.github.magisk317.relay.xp.hook.code

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsCodePostParseCoordinatorTest {

    @Test
    fun createParsedSmsPlan_mapsEnabledSettingsIntoExecutionPlan() {
        val settings = SmsCodePostParseCoordinator.Settings(
            showNotification = true,
            autoCancelNotification = true,
            notificationRetentionMs = 5_000L,
            autoInputEnabled = true,
            autoInputDelayMs = 1_500L,
            copyToClipboardEnabled = true,
            showToast = true,
            recordSmsEnabled = true,
            blockSmsEnabled = true,
            markAsReadEnabled = true,
            deleteSmsEnabled = false,
            deduplicateSmsEnabled = true,
        )

        val plan = SmsCodePostParseCoordinator.createParsedSmsPlan(
            settings = settings,
            forwardDelayMs = 100L,
        )

        assertTrue(plan.blockSms)
        assertTrue(plan.deduplicateSmsEnabled)
        assertTrue(plan.uiPlan.copyToClipboardEnabled)
        assertTrue(plan.uiPlan.showToast)
        assertEquals(1_500L, plan.autoInputDelayMs)
        assertEquals(5_000L, plan.notificationPlan?.autoCancelDelayMs)
        assertTrue(plan.shouldRecord)
        assertEquals(100L, plan.forwardDelayMs)
        assertEquals(listOf(300L, 1000L, 2000L), plan.operateSmsDelays)
    }

    @Test
    fun createParsedSmsPlan_dropsOptionalActionsWhenSettingsDisabled() {
        val settings = SmsCodePostParseCoordinator.Settings(
            showNotification = false,
            autoCancelNotification = false,
            notificationRetentionMs = 9_000L,
            autoInputEnabled = false,
            autoInputDelayMs = 2_000L,
            copyToClipboardEnabled = false,
            showToast = false,
            recordSmsEnabled = false,
            blockSmsEnabled = false,
            markAsReadEnabled = false,
            deleteSmsEnabled = true,
            deduplicateSmsEnabled = false,
        )

        val plan = SmsCodePostParseCoordinator.createParsedSmsPlan(
            settings = settings,
            forwardDelayMs = 250L,
        )

        assertFalse(plan.blockSms)
        assertFalse(plan.uiPlan.copyToClipboardEnabled)
        assertFalse(plan.uiPlan.showToast)
        assertNull(plan.autoInputDelayMs)
        assertNull(plan.notificationPlan)
        assertFalse(plan.shouldRecord)
        assertEquals(250L, plan.forwardDelayMs)
        assertEquals(listOf(300L), plan.operateSmsDelays)
    }

    @Test
    fun createObservedSmsPlan_disablesRecordWhenDedupEnabled() {
        val settings = SmsCodePostParseCoordinator.Settings(
            showNotification = true,
            autoCancelNotification = false,
            notificationRetentionMs = 5_000L,
            autoInputEnabled = true,
            autoInputDelayMs = 1_000L,
            copyToClipboardEnabled = true,
            showToast = true,
            recordSmsEnabled = true,
            blockSmsEnabled = false,
            markAsReadEnabled = false,
            deleteSmsEnabled = false,
            deduplicateSmsEnabled = true,
        )

        val plan = SmsCodePostParseCoordinator.createObservedSmsPlan(settings)

        assertTrue(plan.autoInputEnabled)
        assertTrue(plan.deduplicateSmsEnabled)
        assertFalse(plan.shouldRecord)
    }
}
