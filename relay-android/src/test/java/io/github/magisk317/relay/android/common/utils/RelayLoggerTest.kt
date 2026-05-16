package io.github.magisk317.relay.android.common.utils

import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RelayLoggerTest {

    @AfterEach
    fun tearDown() {
        XLog.setLogLevel(2)
        XLog.setTestSink(null)
        RelayLogger.setRuntimeSinkForTest(null)
        SensitiveLogPolicy.setEnabled(false)
    }

    @Test
    fun xLogKeepsLogLevelGate() {
        var called = false
        XLog.setLogLevel(5)
        XLog.setTestSink { _, _ -> called = true }

        XLog.d("debug message")

        assertFalse(called)
    }

    @Test
    fun xLogTestSinkReceivesFormattedAndSanitizedMessage() {
        var captured: Pair<Int, String>? = null
        XLog.setLogLevel(2)
        XLog.setTestSink { priority, message -> captured = priority to message }

        XLog.i("token=%s body=%s", "secret123", "验证码123456")

        assertEquals(4, captured?.first)
        assertFalse(captured?.second.orEmpty().contains("secret123"))
        assertFalse(captured?.second.orEmpty().contains("验证码123456"))
        assertTrue(captured?.second.orEmpty().contains("token=***"))
        assertTrue(captured?.second.orEmpty().contains("payload[len="))
    }

    @Test
    fun xLogRuntimeSinkReceivesSanitizedRouteEvent() {
        var captured: CapturedRuntimeLog? = null
        XLog.setLogLevel(2)
        RelayLogger.setRuntimeSinkForTest(
            object : RelayLogger.RuntimeSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                ) {
                    captured = CapturedRuntimeLog(priority, tag, message, force, route)
                }
            },
        )

        XLog.e("sender=13800138000")

        assertEquals(6, captured?.priority)
        assertEquals("relay", captured?.tag)
        assertFalse(captured?.message.orEmpty().contains("13800138000"))
        assertTrue(captured?.force ?: false)
        assertEquals(RuntimeLogStore.ROUTE_APP, captured?.route)
    }

    @Test
    fun relayLoggerAllowsExplicitRouteAndForcePolicy() {
        var captured: CapturedRuntimeLog? = null
        XLog.setLogLevel(2)
        RelayLogger.setRuntimeSinkForTest(
            object : RelayLogger.RuntimeSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                ) {
                    captured = CapturedRuntimeLog(priority, tag, message, force, route)
                }
            },
        )

        RelayLogger.log(
            priority = 3,
            route = RuntimeLogStore.ROUTE_FORWARD,
            force = false,
            sensitive = false,
            message = "forward debug",
        )

        assertEquals(3, captured?.priority)
        assertEquals("forward debug", captured?.message)
        assertFalse(captured?.force ?: true)
        assertEquals(RuntimeLogStore.ROUTE_FORWARD, captured?.route)
    }

    @Test
    fun xLogAllowsExplicitRoute() {
        var captured: CapturedRuntimeLog? = null
        XLog.setLogLevel(2)
        RelayLogger.setRuntimeSinkForTest(
            object : RelayLogger.RuntimeSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                ) {
                    captured = CapturedRuntimeLog(priority, tag, message, force, route)
                }
            },
        )

        XLog.i(LogRoute.ROOT_DB, "root db event")

        assertEquals(4, captured?.priority)
        assertEquals("root db event", captured?.message)
        assertTrue(captured?.force ?: false)
        assertEquals(RuntimeLogStore.ROUTE_ROOT_DB, captured?.route)
    }

    private data class CapturedRuntimeLog(
        val priority: Int,
        val tag: String,
        val message: String,
        val force: Boolean,
        val route: String?,
    )
}
