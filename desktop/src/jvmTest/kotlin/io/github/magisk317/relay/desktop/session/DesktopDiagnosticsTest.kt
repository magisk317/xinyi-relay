package io.github.magisk317.relay.desktop.session

import io.github.magisk317.relay.desktop.local.DesktopDiagnosticsController
import io.github.magisk317.relay.desktop.local.DiagnosticsOutcome
import io.github.magisk317.relay.desktop.platform.DesktopFileDialog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.Instant

/**
 * Contract of the diagnostics export (parity §5, `诊断信息导出`).
 *
 * The load-bearing assertions are the safety ones: the session file holds
 * live tokens, and the bundle is meant to be pasted into a bug report, so no
 * token-shaped string may survive [collectDiagnostics]. The rest pins the
 * Legacy-compatible section shapes (connection / session / null notifications)
 * and the controller's dialog + atomic-write behaviour.
 */
class DesktopDiagnosticsTest {

    private fun store(dir: Path) = ProfileStore(dir, InMemoryDesktopCredentialStore())

    @Test
    fun `report carries no token material`(@TempDir dir: Path) {
        val session = signedInSession(dir, accessToken = "SECRET-ACCESS-TOKEN", refreshToken = "SECRET-REFRESH-TOKEN")

        val text = collectDiagnostics(session, mirror = null).encode()

        assertFalse(text.contains("SECRET-ACCESS-TOKEN"), "access token leaked into the bundle")
        assertFalse(text.contains("SECRET-REFRESH-TOKEN"), "refresh token leaked into the bundle")
        assertFalse(text.contains("accessToken"), "bundle must not even name the token fields")
        assertFalse(text.contains("refreshToken"), "bundle must not even name the token fields")
        assertTrue(text.contains("tester"), "the username summary is expected, saw: $text")
    }

    @Test
    fun `every section round trips and the session projection stays token free`(@TempDir dir: Path) {
        val session = signedInSession(dir, accessToken = "tok-a", refreshToken = "tok-r")
        val mirror = io.github.magisk317.relay.desktop.local.LocalSyncStatus(
            devices = 2,
            records = 5,
            syncing = true,
        )

        val diagnostics = collectDiagnostics(
            session,
            mirror = mirror,
            clock = { Instant.parse("2026-10-03T12:34:56Z") },
        )
        val decoded = DesktopDiagnostics.decode(diagnostics.encode())

        assertEquals("2026-10-03T12:34:56Z", decoded.createdAt)
        assertEquals("xinyi-relay-desktop-kmp", decoded.app.name)
        assertTrue(decoded.app.version.isNotBlank(), "the version stamp must carry something")
        assertTrue(decoded.app.os.isNotBlank())
        assertEquals(1, decoded.profiles.size)
        assertNull(decoded.notifications, "the KMP shell has no notification switches")
        assertEquals("local", decoded.connection.state)
        assertEquals(
            "profile-1/tester/2026-10-03T13:00:00Z/2026-10-10T12:00:00Z",
            decoded.session?.let {
                "${it.profileId}/${it.username}/${it.expiresAt}/${it.refreshExpiresAt}"
            },
            "session projection must match the legacy field set",
        )
        // The run mode travels in the KMP envelope shape (the PascalCase name
        // the enum serializes as in profiles.json), not the Rust camelCase one:
        // the legacy diagnostics bundle has no run mode field at all.
        assertEquals("Local", decoded.runMode)
        assertEquals(true, decoded.mirror?.active, "Local mode uses the mirror")
        assertEquals(2, decoded.mirror?.devices)
        assertEquals(5, decoded.mirror?.records)
        assertEquals(true, decoded.mirror?.syncing)
    }

    @Test
    fun `without a stored session the projection and mirror sections stay empty`(@TempDir dir: Path) {
        val store = store(dir)
        val session = DesktopSessionState(store)
        session.saveProfile("https://console.example.com")

        val diagnostics = collectDiagnostics(session, mirror = null)

        assertNull(diagnostics.session, "no session file means no session section")
        assertNull(diagnostics.mirror, "no footer status means no mirror section")
        assertEquals(1, diagnostics.profiles.size)
    }

    @Test
    fun `export writes a decodable file and a cancelled dialog writes nothing`(@TempDir dir: Path) = kotlinx.coroutines.runBlocking {
        val session = signedInSession(dir, accessToken = "tok-a", refreshToken = "tok-r")
        // The temp dir also holds the profile store files, so the export gets
        // its own folder; a cancelled dialog must leave that folder untouched.
        val exportDir = File(dir.toFile(), "export").apply { mkdirs() }
        val controller = DesktopDiagnosticsController(
            report = { collectDiagnostics(session, mirror = null) },
            fileDialog = FakeFileDialog(saveResult = null),
            clock = { Instant.parse("2026-10-03T12:34:56Z") },
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )

        val cancelled = controller.export()
        assertEquals(DiagnosticsOutcome.Cancelled, cancelled)
        assertTrue(exportDir.listFiles()!!.isEmpty(), "a cancelled export must leave nothing behind")

        val target = File(exportDir, "diag.json")
        val dialog = FakeFileDialog(saveResult = target)
        val writer = DesktopDiagnosticsController(
            report = { collectDiagnostics(session, mirror = null) },
            fileDialog = dialog,
            clock = { Instant.parse("2026-10-03T12:34:56Z") },
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val outcome = writer.export()
        assertTrue(outcome is DiagnosticsOutcome.Exported, "export must succeed, saw $outcome")
        assertTrue(target.isFile)
        val written = DesktopDiagnostics.decode(target.readText())
        assertEquals("tester", written.session?.username)
        assertTrue(
            dialog.lastSuggestedName!!.matches(Regex("xinyi-relay-diagnostics-\\d{8}-\\d{6}\\.json")),
            "the suggested name must carry a UTC stamp, saw ${dialog.lastSuggestedName}",
        )
    }

    /** Persists a profile plus a session file, so [collectDiagnostics] sees both. */
    private fun signedInSession(dir: Path, accessToken: String, refreshToken: String): DesktopSessionState {
        val store = store(dir)
        val session = DesktopSessionState(store)
        // saveProfile is the public path that sets activeProfile; the session
        // file is written directly underneath it (login would need a network).
        val profile = session.saveProfile("https://console.example.com")
        store.saveSession(
            DesktopSession(
                profileId = profile.id,
                username = "tester",
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = "2026-10-03T13:00:00Z",
                refreshExpiresAt = "2026-10-10T12:00:00Z",
            ),
        )
        return session
    }

    private class FakeFileDialog(private val saveResult: File?) : DesktopFileDialog {
        var lastSuggestedName: String? = null
            private set

        override fun chooseSaveFile(suggestedName: String): File? {
            lastSuggestedName = suggestedName
            return saveResult
        }

        override fun chooseOpenFile(): File? = error("the diagnostics export never opens files")
    }
}
