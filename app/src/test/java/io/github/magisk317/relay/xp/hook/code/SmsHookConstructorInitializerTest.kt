package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeContext
import io.github.magisk317.smscode.core.utils.XLog
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

class SmsHookConstructorInitializerTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun handle_stopsWhenRuntimeUnavailable() {
        stubXLog()
        val phoneContext = mockk<Context>(relaxed = true)
        var activationCalls = 0
        val initializer = SmsHookConstructorInitializer(
            runtimeInitializer = { null },
            activationMarker = { activationCalls += 1 },
        )

        val outcome = initializer.handle(phoneContext)

        assertEquals(SmsHookConstructorInitializer.StopReason.RUNTIME_UNAVAILABLE, outcome.stopReason)
        assertFalse(outcome.initialized)
        assertEquals(0, activationCalls)
    }

    @Test
    fun handle_registersNotificationAndObserverWhenHealthy() {
        stubXLog()
        val runtime = runtime()
        var conflictChannelCalls = 0
        var notificationChannelCalls = 0
        var copyReceiverCalls = 0
        var activationContext: Context? = null
        var heartbeatSource: String? = null
        var observerCalls = 0
        val initializer = SmsHookConstructorInitializer(
            runtimeInitializer = { runtime },
            conflictNoticeChannelInitializer = { _, _ -> conflictChannelCalls += 1 },
            conflictSuppressor = { _, _ -> false },
            showNotificationReader = { true },
            notificationChannelInitializer = { notificationChannelCalls += 1 },
            copyCodeRegistrar = { copyReceiverCalls += 1 },
            activationMarker = { context -> activationContext = context },
            heartbeatRecorder = { source -> heartbeatSource = source },
            inboxObserverRegistrar = { observerCalls += 1 },
        )

        val outcome = initializer.handle(runtime.phoneContext)

        assertNull(outcome.stopReason)
        assertFalse(outcome.suppressedByRelay)
        assertEquals(1, conflictChannelCalls)
        assertEquals(1, notificationChannelCalls)
        assertEquals(1, copyReceiverCalls)
        assertEquals(runtime.pluginContext, activationContext)
        assertEquals("sms_handler_constructor", heartbeatSource)
        assertEquals(1, observerCalls)
    }

    @Test
    fun handle_suppressedConstructorSkipsReceiverAndRegistersSuppression() {
        stubXLog()
        val runtime = runtime()
        var copyReceiverCalls = 0
        var suppressionStage: String? = null
        var observerCalls = 0
        val initializer = SmsHookConstructorInitializer(
            runtimeInitializer = { runtime },
            conflictNoticeChannelInitializer = { _, _ -> },
            conflictSuppressor = { _, _ -> true },
            showNotificationReader = { true },
            notificationChannelInitializer = {},
            copyCodeRegistrar = { copyReceiverCalls += 1 },
            activationMarker = {},
            heartbeatRecorder = {},
            suppressionLogger = { stage -> suppressionStage = stage },
            inboxObserverRegistrar = { observerCalls += 1 },
        )

        val outcome = initializer.handle(runtime.phoneContext)

        assertTrue(outcome.initialized)
        assertTrue(outcome.suppressedByRelay)
        assertEquals(0, copyReceiverCalls)
        assertEquals("constructor", suppressionStage)
        assertEquals(0, observerCalls)
    }

    private fun runtime(): SmsHookRuntimeContext {
        return SmsHookRuntimeContext(
            pluginContext = mockk<Context>(relaxed = true),
            phoneContext = mockk<Context>(relaxed = true),
        )
    }

    private fun stubXLog() {
        mockkObject(XLog)
        every { XLog.e(any(), *anyVararg()) } returns Unit
    }
}
