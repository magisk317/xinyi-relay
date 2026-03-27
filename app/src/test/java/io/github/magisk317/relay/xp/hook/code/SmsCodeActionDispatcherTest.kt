package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.os.Handler
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsCodeActionDispatcherTest {

    @Test
    fun dispatchParsedSmsActions_routesEachEnabledActionToScheduler() {
        val uiHandler = mockk<Handler>(relaxed = true)
        val executor = mockk<ScheduledExecutorService>(relaxed = true)
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val smsMsg = smsMsg()
        val plan = SmsCodePostParseCoordinator.ParsedSmsPlan(
            blockSms = true,
            deduplicateSmsEnabled = true,
            uiPlan = SmsCodePostParseCoordinator.UiPlan(
                copyToClipboardEnabled = true,
                showToast = true,
            ),
            autoInputDelayMs = 1_500L,
            notificationPlan = SmsCodePostParseCoordinator.NotificationPlan(autoCancelDelayMs = 5_000L),
            shouldRecord = true,
            operateSmsDelays = listOf(300L, 1000L),
        )
        var uiDispatched = false
        var autoInputDelay: Long? = null
        var notificationDelay: Long? = null
        var recordEventId: String? = null
        var operateSmsDelays: List<Long>? = null

        SmsCodeActionDispatcher.dispatchParsedSmsActions(
            uiHandler = uiHandler,
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = "evt-1",
            plan = plan,
            uiDispatcher = { _, _, _, _, uiPlan ->
                uiDispatched = uiPlan.copyToClipboardEnabled && uiPlan.showToast
            },
            autoInputScheduler = { _, _, _, _, delayMs, _ ->
                autoInputDelay = delayMs
            },
            notificationScheduler = { _, _, _, _, notificationPlan ->
                notificationDelay = notificationPlan.autoCancelDelayMs
            },
            recordScheduler = { _, _, _, _, eventId, _ ->
                recordEventId = eventId
            },
            operateSmsScheduler = { _, _, _, _, delays ->
                operateSmsDelays = delays
            },
        )

        assertTrue(uiDispatched)
        assertEquals(1_500L, autoInputDelay)
        assertEquals(5_000L, notificationDelay)
        assertEquals("evt-1", recordEventId)
        assertEquals(listOf(300L, 1000L), operateSmsDelays)
    }

    @Test
    fun dispatchObservedSmsActions_runsOnlyEnabledImmediateActions() {
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val smsMsg = smsMsg()
        val plan = SmsCodePostParseCoordinator.ObservedSmsPlan(
            deduplicateSmsEnabled = true,
            autoInputEnabled = true,
            shouldRecord = false,
        )
        var autoInputDedup: Boolean? = null
        var recordCalled = false

        SmsCodeActionDispatcher.dispatchObservedSmsActions(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = "evt-2",
            plan = plan,
            autoInputRunner = { _, _, _, deduplicateEnabled ->
                autoInputDedup = deduplicateEnabled
            },
            recordRunner = { _, _, _, _, _ ->
                recordCalled = true
            },
        )

        assertEquals(true, autoInputDedup)
        assertEquals(false, recordCalled)
    }

    @Test
    fun dispatchParsedSmsActions_delaysToastUntilAfterAutoInputAttemptWindow() {
        val uiHandler = mockk<Handler>(relaxed = true)
        val executor = mockk<ScheduledExecutorService>(relaxed = true)
        every {
            executor.schedule(any<Runnable>(), any<Long>(), any<TimeUnit>())
        } returns mockk<ScheduledFuture<*>>(relaxed = true)

        SmsCodeActionDispatcher.dispatchParsedSmsActions(
            uiHandler = uiHandler,
            executor = executor,
            pluginContext = mockk(relaxed = true),
            phoneContext = mockk(relaxed = true),
            smsMsg = smsMsg(),
            eventId = "evt-3",
            plan = SmsCodePostParseCoordinator.ParsedSmsPlan(
                blockSms = false,
                deduplicateSmsEnabled = true,
                uiPlan = SmsCodePostParseCoordinator.UiPlan(
                    copyToClipboardEnabled = false,
                    showToast = true,
                ),
                autoInputDelayMs = 1_500L,
                notificationPlan = null,
                shouldRecord = false,
                operateSmsDelays = emptyList(),
            ),
            autoInputScheduler = { _, _, _, _, _, _ -> },
            notificationScheduler = { _, _, _, _, _ -> },
            recordScheduler = { _, _, _, _, _, _ -> },
            operateSmsScheduler = { _, _, _, _, _ -> },
        )

        verify {
            executor.schedule(any<Runnable>(), 1_750L, TimeUnit.MILLISECONDS)
        }
    }

    private fun smsMsg(): SmsMsg {
        return SmsMsg(
            sender = "1068",
            body = "otp 123456",
            date = 100L,
            msgType = SmsMsg.MSG_TYPE_SMS,
        )
    }
}
