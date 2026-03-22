package io.github.magisk317.relay.xp.hook

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SmsHookRuntimeSessionTest {

    @Test
    fun initialize_resolvesPluginContextOnce() {
        val phoneContext = mockk<Context>(relaxed = true)
        val pluginContext = mockk<Context>(relaxed = true)
        var resolveCalls = 0
        val session = SmsHookRuntimeSession(
            applicationId = "io.github.magisk317.test",
            packageName = "com.android.phone",
            pluginContextResolver = { context, applicationId ->
                resolveCalls += 1
                assertEquals(phoneContext, context)
                assertEquals("io.github.magisk317.test", applicationId)
                pluginContext
            },
        )

        val first = session.initialize(phoneContext)
        val second = session.currentOrResolve()

        assertNotNull(first)
        assertEquals(phoneContext, first!!.phoneContext)
        assertEquals(pluginContext, first.pluginContext)
        assertEquals(first, second)
        assertEquals(1, resolveCalls)
    }

    @Test
    fun currentOrResolve_returnsNullBeforeInitialize() {
        val session = SmsHookRuntimeSession(
            applicationId = "io.github.magisk317.test",
            packageName = "com.android.phone",
        )

        assertNull(session.currentOrResolve())
    }

    @Test
    fun recordHeartbeat_usesResolvedRuntimeContexts() {
        val phoneContext = mockk<Context>(relaxed = true)
        val pluginContext = mockk<Context>(relaxed = true)
        var recordedSource: String? = null
        var recordedPackageName: String? = null
        val session = SmsHookRuntimeSession(
            applicationId = "io.github.magisk317.test",
            packageName = "com.android.phone",
            pluginContextResolver = { _, _ -> pluginContext },
            heartbeatRecorder = { recordedPluginContext, recordedPhoneContext, packageName, source ->
                assertEquals(pluginContext, recordedPluginContext)
                assertEquals(phoneContext, recordedPhoneContext)
                recordedPackageName = packageName
                recordedSource = source
            },
        )

        val runtime = session.initialize(phoneContext)
        val heartbeatRuntime = session.recordHeartbeat("sms_forward_dispatch")

        assertEquals(runtime, heartbeatRuntime)
        assertEquals("com.android.phone", recordedPackageName)
        assertEquals("sms_forward_dispatch", recordedSource)
    }
}
