package io.github.magisk317.relay.desktop.session

import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DesktopSessionStateTest {

    @TempDir
    lateinit var dir: Path

    private val credentials = InMemoryDesktopCredentialStore()

    @Test
    fun `run mode starts at the default`() {
        val state = DesktopSessionState(ProfileStore(dir, credentials))
        assertEquals(DesktopRunMode.Default, state.runMode)
    }

    @Test
    fun `switchRunMode publishes and persists`() {
        val state = DesktopSessionState(ProfileStore(dir, credentials))
        state.switchRunMode(DesktopRunMode.Hybrid)
        assertEquals(DesktopRunMode.Hybrid, state.runMode)
        assertEquals(DesktopRunMode.Hybrid, ProfileStore(dir, credentials).loadState().runMode)
        state.switchRunMode(DesktopRunMode.Local)
        assertEquals(DesktopRunMode.Local, state.runMode)
        assertEquals(DesktopRunMode.Local, ProfileStore(dir, credentials).loadState().runMode)
    }

    @Test
    fun `bootstrap surfaces the persisted run mode before probing`() = runBlocking {
        ProfileStore(dir, credentials).saveState(PersistedDesktopState(runMode = DesktopRunMode.Hybrid))
        val state = DesktopSessionState(ProfileStore(dir, credentials))
        state.bootstrap()
        withTimeout(5.seconds) {
            while (state.runMode != DesktopRunMode.Hybrid) delay(10)
        }
        assertEquals(DesktopRunMode.Hybrid, state.runMode)
    }
}
