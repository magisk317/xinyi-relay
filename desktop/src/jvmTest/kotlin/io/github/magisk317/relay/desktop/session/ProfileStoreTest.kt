package io.github.magisk317.relay.desktop.session

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ProfileStoreTest {

    @TempDir
    lateinit var dir: Path

    private val credentials = InMemoryDesktopCredentialStore()

    private fun store() = ProfileStore(dir, credentials)

    private fun session(id: String = "p1") = DesktopSession(
        profileId = id,
        username = "alice",
        accessToken = "a1",
        refreshToken = "r1",
        expiresAt = "2026-10-02T00:00:00Z",
        refreshExpiresAt = "2026-11-02T00:00:00Z",
    )

    @Test
    fun `state round trips`() {
        val store = store()
        val profile = DesktopProfile(id = "p1", name = "prod", baseUrl = "https://relay.example.com")
        store.saveState(PersistedDesktopState(profiles = listOf(profile), activeProfileId = "p1"))
        val loaded = store.loadState()
        assertEquals(1, loaded.profiles.size)
        assertEquals("p1", loaded.activeProfileId)
        assertEquals("https://relay.example.com", loaded.profiles[0].baseUrl)
    }

    @Test
    fun `state file predating run mode defaults to Local`() {
        Files.writeString(dir.resolve("profiles.json"), """{"profiles":[],"activeProfileId":null}""")
        assertEquals(DesktopRunMode.Local, store().loadState().runMode)
    }

    @Test
    fun `run mode round trips through the store`() {
        val store = store()
        store.saveState(PersistedDesktopState(runMode = DesktopRunMode.Hybrid))
        assertEquals(DesktopRunMode.Hybrid, store.loadState().runMode)
        store.saveState(store.loadState().copy(runMode = DesktopRunMode.Local))
        assertEquals(DesktopRunMode.Local, store.loadState().runMode)
    }

    @Test
    fun `session round trips and is private`() {
        val store = store()
        store.saveSession(session())
        assertEquals(session(), store.loadSession("p1"))
        val perms = Files.getPosixFilePermissions(dir.resolve("session-p1.json"))
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms)
        val raw = Files.readString(dir.resolve("session-p1.json"))
        assertFalse(raw.contains("a1"))
        assertFalse(raw.contains("r1"))
    }

    @Test
    fun `missing session is null and clear is idempotent`() {
        val store = store()
        assertNull(store.loadSession("nope"))
        store.clearSession("nope")
        store.saveSession(session("p2"))
        store.clearSession("p2")
        assertNull(store.loadSession("p2"))
        assertFalse(Files.exists(dir.resolve("session-p2.json")))
    }

    @Test
    fun `empty dir yields empty state`() {
        assertTrue(store().loadState().profiles.isEmpty())
    }
}
