package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.os.Handler
import dev.mokkery.MockMode.autofill
import dev.mokkery.mock
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpSharedRuntimeGate
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.ScheduledExecutorService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsCodeActionDispatcherTest {
    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
    }

    @Test
    fun dispatchParsedSmsActions_routesEachEnabledActionToScheduler() {
        val uiHandler = mock<Handler>(autofill)
        val executor = mock<ScheduledExecutorService>(autofill)
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
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
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
        val smsMsg = smsMsg()
        val plan = SmsCodePostParseCoordinator.ObservedSmsPlan(
            deduplicateSmsEnabled = true,
            autoInputEnabled = true,
            autoInputDelayMs = 0L,
            shouldRecord = false,
        )
        var autoInputDedup: Boolean? = null
        var recordCalled = false

        SmsCodeActionDispatcher.dispatchObservedSmsActions(
            executor = null,
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
    fun resolveToastDelayMs_addsBufferAfterAutoInputAttemptWindow() {
        assertEquals(1_750L, SmsCodeActionDispatcher.resolveToastDelayMs(1_500L))
        assertEquals(0L, SmsCodeActionDispatcher.resolveToastDelayMs(null))
    }

    @Test
    fun claimAutoInputDispatch_skipsSecondMessageWhenCodeMatchesWithinWindow() {
        XLog.setTestSink { _, _ -> }
        val pluginContext = mock<Context>(autofill)
        val claimedAt = LinkedHashMap<String, Long>()
        var now = 5_000L
        val gateClaimer =
            { _: Context, _: String, keys: List<String>, windowMs: Long, _: Int ->
                val blockedKey = keys.firstOrNull { key ->
                    val claimedTime = claimedAt[key]
                    claimedTime != null && now - claimedTime <= windowMs
                }
                if (blockedKey != null) {
                    XpSharedRuntimeGate.ClaimResult(
                        claimed = false,
                        ageMs = now - (claimedAt[blockedKey] ?: now),
                        key = blockedKey,
                    )
                } else {
                    keys.forEach { key -> claimedAt[key] = now }
                    XpSharedRuntimeGate.ClaimResult(claimed = true)
                }
            }

        val firstClaimed = SmsCodeActionDispatcher.claimAutoInputDispatch(
            pluginContext = pluginContext,
            smsMsg = smsMsg(
                sender = "10690665401526040",
                body = "京东云验证码 063664",
                company = "京东云",
                smsCode = "063664",
            ),
            delayMs = 0L,
            gateClaimer = gateClaimer,
        )

        now += 1_000L

        val secondClaimed = SmsCodeActionDispatcher.claimAutoInputDispatch(
            pluginContext = pluginContext,
            smsMsg = smsMsg(
                sender = "JDCloud",
                body = "验证码 063664，请勿泄露",
                company = "京东云",
                smsCode = "063664",
            ),
            delayMs = 0L,
            gateClaimer = gateClaimer,
        )

        assertTrue(firstClaimed)
        assertFalse(secondClaimed)
    }

    private fun smsMsg(
        sender: String = "1068",
        body: String = "otp 123456",
        company: String? = null,
        smsCode: String? = null,
    ): SmsMsg {
        return SmsMsg(
            sender = sender,
            body = body,
            date = 100L,
            company = company,
            smsCode = smsCode,
            msgType = SmsMsg.MSG_TYPE_SMS,
        )
    }
}
