package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.smscode.xposed.utils.XLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObservedSmsHandlerTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun handle_skipsDispatchWhenConflictSuppressed() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        var roleLogCount = 0
        var dispatchCount = 0
        val handler = ObservedSmsHandler(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            settingsLoader = { settings() },
            planFactory = { observedPlan() },
            moduleEnabledReader = { true },
            conflictSuppressor = { _, _ -> true },
            roleStateLogger = { roleLogCount++ },
            duplicateChecker = { _, _, _, _ -> false },
            dispatcher = { _, _, _, _, _ -> dispatchCount++ },
        )

        val outcome = handler.handle(observedRecord())

        assertEquals(SmsInboxObserverDecision.SkipReason.CONFLICT_SUPPRESSED, outcome.decision.skipReason)
        assertFalse(outcome.dispatched)
        assertEquals(0, roleLogCount)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun handle_dispatchesObservedSmsWhenHealthy() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        var loggedEventId: String? = null
        var dispatchedEventId: String? = null
        var dispatchedSms: SmsMsg? = null
        var dispatchedPlan: SmsCodePostParseCoordinator.ObservedSmsPlan? = null
        val handler = ObservedSmsHandler(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            settingsLoader = { settings(recordSmsEnabled = true, deduplicateSmsEnabled = false) },
            planFactory = { observedPlan(autoInputEnabled = true, shouldRecord = true) },
            moduleEnabledReader = { true },
            conflictSuppressor = { _, _ -> false },
            roleStateLogger = { eventId -> loggedEventId = eventId },
            duplicateChecker = { _, _, _, _ -> false },
            smsEnricher = { _, sender, body, date, code ->
                SmsMsg(
                    sender = sender,
                    body = "$body#$code",
                    date = date,
                    msgType = SmsMsg.MSG_TYPE_SMS,
                )
            },
            dispatcher = { _, _, smsMsg, eventId, plan ->
                dispatchedSms = smsMsg
                dispatchedEventId = eventId
                dispatchedPlan = plan
            },
        )

        val outcome = handler.handle(
            observedRecord(
                smsId = 321L,
                sender = "10010",
                body = "otp 654321",
                date = 1_700_000_000_000L,
                code = "654321",
            ),
        )

        assertTrue(outcome.dispatched)
        assertNull(outcome.decision.skipReason)
        assertEquals("sms_observed_loyw3v28_8x", outcome.eventId)
        assertEquals(outcome.eventId, loggedEventId)
        assertEquals(outcome.eventId, dispatchedEventId)
        assertEquals("10010", dispatchedSms?.sender)
        assertEquals("otp 654321#654321", dispatchedSms?.body)
        assertTrue(dispatchedPlan?.autoInputEnabled == true)
        assertTrue(dispatchedPlan?.shouldRecord == true)
    }

    @Test
    fun handle_skipsDispatchWhenSmsAlreadyRead() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        var roleLogCount = 0
        var dispatchCount = 0
        val handler = ObservedSmsHandler(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            settingsLoader = { settings() },
            planFactory = { observedPlan() },
            moduleEnabledReader = { true },
            conflictSuppressor = { _, _ -> false },
            roleStateLogger = { roleLogCount++ },
            duplicateChecker = { _, _, _, _ -> false },
            dispatcher = { _, _, _, _, _ -> dispatchCount++ },
        )

        val outcome = handler.handle(observedRecord(read = true))

        assertFalse(outcome.dispatched)
        assertEquals(0, roleLogCount)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun handle_skipsDispatchWhenObservedSmsAlreadyClaimed() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        var roleLogCount = 0
        var dispatchCount = 0
        val handler = ObservedSmsHandler(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            settingsLoader = { settings() },
            planFactory = { observedPlan() },
            moduleEnabledReader = { true },
            conflictSuppressor = { _, _ -> false },
            sharedGateClaimer = { _, _, _, _, _ -> io.github.magisk317.relay.common.utils.SharedRuntimeGate.ClaimResult(claimed = false, ageMs = 12L) },
            roleStateLogger = { roleLogCount++ },
            duplicateChecker = { _, _, _, _ -> false },
            dispatcher = { _, _, _, _, _ -> dispatchCount++ },
        )

        val outcome = handler.handle(observedRecord())

        assertFalse(outcome.dispatched)
        assertEquals(0, roleLogCount)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun handle_usesCurrentTimeForMissingSmsDate() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        var dispatchedEventId: String? = null
        val handler = ObservedSmsHandler(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            settingsLoader = { settings() },
            planFactory = { observedPlan() },
            moduleEnabledReader = { true },
            conflictSuppressor = { _, _ -> false },
            duplicateChecker = { _, _, _, _ -> false },
            dispatcher = { _, _, _, eventId, _ -> dispatchedEventId = eventId },
            currentTimeMillis = { 1_234_567_890L },
        )

        val outcome = handler.handle(observedRecord(smsId = 36L, date = 0L))

        assertTrue(outcome.dispatched)
        assertEquals("sms_observed_kf12oi_10", outcome.eventId)
        assertEquals(outcome.eventId, dispatchedEventId)
    }

    private fun observedRecord(
        smsId: Long = 123L,
        sender: String = "1068",
        body: String = "otp 123456",
        date: Long = 1_700_000_000_123L,
        read: Boolean = false,
        code: String = "123456",
        triggerUri: String = "content://sms/inbox/123",
    ): ObservedInboxScanRecord {
        return ObservedInboxScanRecord(
            smsId = smsId,
            triggerUri = triggerUri,
            sender = sender,
            body = body,
            date = date,
            read = read,
            code = code,
        )
    }

    private fun settings(
        recordSmsEnabled: Boolean = true,
        deduplicateSmsEnabled: Boolean = false,
    ): SmsCodePostParseCoordinator.Settings {
        return SmsCodePostParseCoordinator.Settings(
            showNotification = false,
            autoCancelNotification = false,
            notificationRetentionMs = 0L,
            autoInputEnabled = true,
            autoInputDelayMs = 0L,
            copyToClipboardEnabled = false,
            showToast = false,
            recordSmsEnabled = recordSmsEnabled,
            blockSmsEnabled = false,
            markAsReadEnabled = false,
            deleteSmsEnabled = false,
            deduplicateSmsEnabled = deduplicateSmsEnabled,
        )
    }

    private fun observedPlan(
        autoInputEnabled: Boolean = true,
        shouldRecord: Boolean = true,
        deduplicateSmsEnabled: Boolean = false,
    ): SmsCodePostParseCoordinator.ObservedSmsPlan {
        return SmsCodePostParseCoordinator.ObservedSmsPlan(
            deduplicateSmsEnabled = deduplicateSmsEnabled,
            autoInputEnabled = autoInputEnabled,
            shouldRecord = shouldRecord,
        )
    }

    private fun stubXLog() {
        mockkObject(XLog)
        every { XLog.w(any(), *anyVararg()) } returns Unit
        every { XLog.i(any(), *anyVararg()) } returns Unit
    }
}
