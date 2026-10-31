package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardEventDao
import io.github.magisk317.forward.db.ForwardMetaDao
import io.github.magisk317.forward.db.ForwardMetaEntity
import io.github.magisk317.relay.android.data.db.ForwardLegacyImporter
import io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog
import io.github.magisk317.relay.android.data.db.legacy.LegacyConfigRows
import io.github.magisk317.relay.android.data.db.legacy.LegacyMigrationSource
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacySmsMsg as SmsMsg
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The readiness gate must fail closed: a parity or I/O failure has to record the stage
 * that broke and rethrow, so readers never see a half-populated forward store. On full
 * success the legacy source is marked drained and its tables are dropped.
 */
class ForwardDatabaseBootstrapperTest {

    private class FakeLegacySource : LegacyMigrationSource {
        var fileExists = true
        var markerWritten = false
        var dropped = false
        var closed = false
        var smsMsgs: List<SmsMsg> = emptyList()
        var dispatchLogs: List<SenderDispatchLog> = emptyList()
        var config = LegacyConfigRows(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        override fun legacyFileExists() = fileExists
        override fun readSmsMsgs() = smsMsgs
        override fun readDispatchLogs() = dispatchLogs
        override fun readConfig() = config
        override fun markDrained() { markerWritten = true }
        override fun dropLegacyTables() { dropped = true }
        override fun close() { closed = true }
    }

    private lateinit var source: FakeLegacySource
    private lateinit var target: ForwardDatabase
    private lateinit var eventDao: ForwardEventDao
    private lateinit var metaDao: ForwardMetaDao

    @BeforeEach
    fun setUp() {
        source = FakeLegacySource()
        target = mockk(relaxed = true)
        eventDao = mockk(relaxed = true)
        metaDao = mockk(relaxed = true)
        every { target.forwardEventDao() } returns eventDao
        every { target.forwardMetaDao() } returns metaDao
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `import marks drained and drops legacy tables on success`() = runBlocking {
        val firstMsg = SmsMsg(id = 1, sender = "1068", body = "code 123456", date = 100L)
        source.smsMsgs = listOf(firstMsg)
        coEvery { eventDao.count() } returns 0
        coEvery { eventDao.insertAll(any()) } returns listOf(11L)
        // Parity re-reads the stored row and compares it against the projection
        // (id-normalized), so the stub must return the projected content.
        coEvery { eventDao.byId(11L) } answers { ForwardLegacyImporter.projectEvent(firstMsg).copy(id = 11L) }

        ForwardDatabaseBootstrapper.importLegacyData(source, target)

        assertTrue(source.markerWritten, "drain marker must be written on success")
        assertTrue(source.dropped, "legacy tables must be dropped on success")
        assertTrue(source.closed.not(), "close is owned by the caller")
    }

    @Test
    fun `import clears the failure marker when both stages are already done`() = runBlocking {
        coEvery { eventDao.count() } returns 7
        coEvery { metaDao.get("legacy_config_import") } returns "ok"

        ForwardDatabaseBootstrapper.importLegacyData(source, target)

        coVerify { metaDao.remove("legacy_import_failure") }
        coVerify(exactly = 0) { metaDao.put(any()) }
        assertTrue(source.markerWritten)
        assertTrue(source.dropped)
    }

    @Test
    fun `import fails closed and records the failing stage when event parity breaks`() = runBlocking {
        source.smsMsgs = listOf(SmsMsg(id = 1, sender = "1068", body = "code 123456", date = 100L))
        coEvery { eventDao.count() } returns 0
        mockkObject(ForwardLegacyImporter)
        coEvery {
            ForwardLegacyImporter.importAll(any(), any(), target)
        } returns ForwardLegacyImporter.ImportReport(
            eventsImported = 0,
            dispatchLogsImported = 0,
            parity = ForwardLegacyImporter.ParityReport(
                eventCountMatches = false,
                dispatchLogCountMatches = false,
                firstEventMismatch = "missing event for legacy id=1",
            ),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { ForwardDatabaseBootstrapper.importLegacyData(source, target) }
        }

        assertTrue(error.message!!.contains("parity"), "message=${error.message}")
        coVerify { metaDao.put(ForwardMetaEntity("legacy_event_import", "failed")) }
        coVerify { metaDao.put(match { it.key == "legacy_import_failure" && it.value == "IllegalStateException" }) }
        coVerify(exactly = 0) { metaDao.remove("legacy_import_failure") }
        assertFalse(source.markerWritten, "a failed import must not mark the source drained")
        assertFalse(source.dropped, "a failed import must keep the legacy tables")
    }

    @Test
    fun `import fails closed on a storage error without touching the marker`() = runBlocking {
        coEvery { eventDao.count() } throws IllegalStateException("disk gone")

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { ForwardDatabaseBootstrapper.importLegacyData(source, target) }
        }

        assertEquals("disk gone", error.message)
        coVerify { metaDao.put(ForwardMetaEntity("legacy_event_import", "failed")) }
        coVerify(exactly = 0) { metaDao.remove("legacy_import_failure") }
        assertFalse(source.markerWritten)
        assertFalse(source.dropped)
    }
}
