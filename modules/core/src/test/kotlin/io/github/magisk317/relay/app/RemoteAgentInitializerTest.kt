package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.contract.repository.ConfigSyncCoordinator
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteAgentInitializerTest {
    @Test
    fun init_defersProviderObserverAndCoordinatorUntilUserUnlock() {
        val application = Application()
        val coordinator = mockk<ConfigSyncCoordinator>(relaxed = true)
        var coordinatorProviderCalls = 0
        var observedApplication: Application? = null
        var providerChanged: (() -> Unit)? = null
        var unlockBlock: (suspend () -> Unit)? = null
        val initializer = RemoteAgentInitializer(
            coordinatorProvider = { incomingApplication ->
                assertSame(application, incomingApplication)
                coordinatorProviderCalls += 1
                coordinator
            },
            recordObserverRegistrar = { incomingApplication, callback ->
                observedApplication = incomingApplication
                providerChanged = callback
            },
            initRunner = { incomingApplication, _, taskName, block ->
                assertSame(application, incomingApplication)
                assertEquals("RemoteAgentInitializer", taskName)
                unlockBlock = block
            },
        )

        initializer.init(application)

        assertEquals(0, coordinatorProviderCalls)
        assertEquals(null, observedApplication)
        assertEquals(null, providerChanged)
        coVerify(exactly = 0) { coordinator.startupSync() }

        runBlocking { unlockBlock?.invoke() }
        providerChanged?.invoke()

        assertEquals(1, coordinatorProviderCalls)
        assertSame(application, observedApplication)
        assertTrue(providerChanged != null)
        verify(exactly = 1) { coordinator.scheduleRecordUpload("provider_change") }
        coVerify(exactly = 1) { coordinator.startupSync() }
    }
}
