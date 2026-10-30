package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardEventDao
import io.github.magisk317.forward.db.ForwardMetaDao
import io.github.magisk317.forward.db.ForwardMetaEntity
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.ForwardLegacyImporter
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The readiness gate must fail closed: a parity or I/O failure has to record the stage
 * that broke and rethrow, so readers never see a half-populated forward store.
 */
class ForwardDatabaseBootstrapperTest {

    private lateinit var legacy: AppDatabase
    private lateinit var target: ForwardDatabase
    private lateinit var eventDao: ForwardEventDao
    private lateinit var metaDao: ForwardMetaDao

    @BeforeEach
    fun setUp() {
        legacy = mockk(relaxed = true)
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
    fun `import clears the failure marker when both stages are already done`() = runBlocking {
        coEvery { eventDao.count() } returns 7
        coEvery { metaDao.get("legacy_config_import") } returns "ok"

        ForwardDatabaseBootstrapper.importLegacyData(legacy, target)

        coVerify { metaDao.remove("legacy_import_failure") }
        coVerify(exactly = 0) { metaDao.put(any()) }
    }

    @Test
    fun `import fails closed and records the failing stage when event parity breaks`() = runBlocking {
        coEvery { eventDao.count() } returns 0
        coEvery { legacy.smsMsgDao().getAll() } returns listOf(
            SmsMsg(id = 1, sender = "1068", body = "code 123456", date = 100L, msgType = SmsMsg.MSG_TYPE_SMS),
        )
        mockkObject(ForwardLegacyImporter)
        coEvery { ForwardLegacyImporter.importAll(legacy, target) } returns ForwardLegacyImporter.ImportReport(
            eventsImported = 0,
            dispatchLogsImported = 0,
            parity = ForwardLegacyImporter.ParityReport(
                eventCountMatches = false,
                dispatchLogCountMatches = false,
                firstEventMismatch = "missing event for legacy id=1",
            ),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { ForwardDatabaseBootstrapper.importLegacyData(legacy, target) }
        }

        assertTrue(error.message!!.contains("parity"), "message=${error.message}")
        coVerify { metaDao.put(ForwardMetaEntity("legacy_event_import", "failed")) }
        coVerify { metaDao.put(match { it.key == "legacy_import_failure" && it.value == "IllegalStateException" }) }
        coVerify(exactly = 0) { metaDao.remove("legacy_import_failure") }
    }

    @Test
    fun `import fails closed on a storage error without touching the marker`() = runBlocking {
        coEvery { eventDao.count() } throws IllegalStateException("disk gone")

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { ForwardDatabaseBootstrapper.importLegacyData(legacy, target) }
        }

        assertEquals("disk gone", error.message)
        coVerify { metaDao.put(ForwardMetaEntity("legacy_event_import", "failed")) }
        coVerify(exactly = 0) { metaDao.remove("legacy_import_failure") }
    }
}
