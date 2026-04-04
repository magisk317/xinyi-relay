package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import dev.mokkery.MockMode.autofill
import dev.mokkery.mock
import io.github.magisk317.smscode.verification.SmsInboxSeenTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObservedInboxScannerTest {

    @Test
    fun scan_filtersDuplicateAndBlankCodeRows() {
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
        val scanner = ObservedInboxScanner(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsIdTracker = SmsInboxSeenTracker(maxTrackedSmsIds = 8),
            smsCodeParser = { _, body ->
                when (body) {
                    "otp 123456" -> "123456"
                    else -> ""
                }
            },
            inboxRowLoader = { _, _ ->
                listOf(
                    ObservedInboxScanner.InboxRow(
                        smsId = 10L,
                        sender = "1068",
                        body = "otp 123456",
                        date = 100L,
                        read = false,
                    ),
                    ObservedInboxScanner.InboxRow(
                        smsId = 10L,
                        sender = "1068",
                        body = "otp 123456",
                        date = 101L,
                        read = false,
                    ),
                    ObservedInboxScanner.InboxRow(
                        smsId = 11L,
                        sender = "10086",
                        body = "hello",
                        date = 102L,
                        read = true,
                    ),
                )
            },
        )

        val result = scanner.scan(triggerUri = "", recentSmsWindowMs = 60_000L)

        assertEquals(1, result.size)
        assertEquals(10L, result[0].smsId)
        assertEquals("1068", result[0].sender)
        assertEquals("123456", result[0].code)
        assertEquals("content://sms", result[0].triggerUri)
    }

    @Test
    fun scan_keepsExplicitTriggerUri() {
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
        val scanner = ObservedInboxScanner(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsIdTracker = SmsInboxSeenTracker(maxTrackedSmsIds = 8),
            smsCodeParser = { _, _ -> "654321" },
            inboxRowLoader = { _, _ ->
                listOf(
                    ObservedInboxScanner.InboxRow(
                        smsId = 12L,
                        sender = "bank",
                        body = "otp",
                        date = 200L,
                        read = false,
                    ),
                )
            },
        )

        val result = scanner.scan(triggerUri = "content://sms/inbox/12", recentSmsWindowMs = 60_000L)

        assertEquals(1, result.size)
        assertEquals("content://sms/inbox/12", result.first().triggerUri)
        assertTrue(result.first().code.isNotBlank())
    }

    @Test
    fun scan_passesTriggeredSmsIdToLoader() {
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
        var receivedTriggeredSmsId: Long? = null
        val scanner = ObservedInboxScanner(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsIdTracker = SmsInboxSeenTracker(maxTrackedSmsIds = 8),
            smsCodeParser = { _, _ -> "888888" },
            inboxRowLoader = { _, triggeredSmsId ->
                receivedTriggeredSmsId = triggeredSmsId
                listOf(
                    ObservedInboxScanner.InboxRow(
                        smsId = 66L,
                        sender = "bank",
                        body = "otp 888888",
                        date = 300L,
                        read = false,
                    ),
                )
            },
        )

        val result = scanner.scan(triggerUri = "content://sms/66", recentSmsWindowMs = 60_000L)

        assertEquals(66L, receivedTriggeredSmsId)
        assertEquals(1, result.size)
        assertEquals(66L, result.first().smsId)
    }
}
