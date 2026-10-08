package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.desktop.data.DatabaseTransfer
import io.github.magisk317.relay.desktop.data.DesktopDatabase
import io.github.magisk317.relay.desktop.data.DesktopDatabaseFactory
import io.github.magisk317.relay.desktop.data.entity.DeviceEntity
import io.github.magisk317.relay.desktop.data.entity.RelayRecordEntity
import io.github.magisk317.relay.desktop.platform.DesktopFileDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

/**
 * Contract of the database transfer seam (parity §5, `数据库导出 / 导入`).
 *
 * The core in `:desktop:data` pins the snapshot format; what is pinned here is
 * the controller around it: it never opens a database itself (it rides the
 * provider the app hands it), a cancelled dialog leaves no file behind, a
 * broken file comes back as a failure instead of an exception, and the
 * suggested export name carries a UTC stamp.
 */
class DatabaseTransferControllerTest {

    @Test
    fun `export writes a snapshot that imports back`(@TempDir dir: File) = runBlocking {
        val source = DesktopDatabaseFactory.inMemory()
        try {
            seed(source)
            val target = File(dir, "snapshot.json")
            val controller = controller(source, FakeFileDialog(saveResult = target))

            val outcome = controller.export()

            assertTrue(outcome is TransferOutcome.Exported, "export must succeed, saw $outcome")
            assertTrue(target.isFile, "the snapshot must land on disk")
            assertEquals(target, (outcome as TransferOutcome.Exported).file)

            val restored = DesktopDatabaseFactory.inMemory()
            try {
                val snapshot = DatabaseTransfer.importText(restored, target.readText())
                assertEquals(1, snapshot.devices.size)
                assertEquals(1, snapshot.records.size)
                assertEquals(1, restored.deviceDao().countActive())
                assertEquals(1, restored.relayRecordDao().listPage(10, 0).size)
            } finally {
                restored.close()
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun `a cancelled dialog writes nothing and still stamps the default name`(@TempDir dir: File) = runBlocking {
        val source = DesktopDatabaseFactory.inMemory()
        try {
            val dialog = FakeFileDialog(saveResult = null)
            val outcome = controller(source, dialog).export()

            assertEquals(TransferOutcome.Cancelled, outcome)
            assertTrue(
                dir.listFiles()!!.isEmpty(),
                "a cancelled export must leave the directory untouched, saw ${dir.listFiles()!!.map { it.name }}",
            )
            assertTrue(
                dialog.lastSuggestedName!!.matches(Regex("xinyi-relay-desktop-\\d{8}-\\d{6}\\.json")),
                "the suggested name must carry a UTC stamp, saw ${dialog.lastSuggestedName}",
            )
        } finally {
            source.close()
        }
    }

    @Test
    fun `import replaces the current content`(@TempDir dir: File) = runBlocking {
        val source = DesktopDatabaseFactory.inMemory()
        try {
            seed(source)
            val snapshotFile = File(dir, "snapshot.json")
            controller(source, FakeFileDialog(saveResult = snapshotFile)).export()

            val database = DesktopDatabaseFactory.inMemory()
            try {
                // Pre-existing content the snapshot must replace wholesale.
                database.deviceDao().insert(device(id = 7L, name = "stale"))
                val controller = controller(database, FakeFileDialog(openResult = snapshotFile))

                val outcome = controller.import()

                assertTrue(outcome is TransferOutcome.Imported, "import must succeed, saw $outcome")
                assertEquals(1, (outcome as TransferOutcome.Imported).snapshot.devices.size)
                assertEquals(listOf(1L), database.deviceDao().listAll().map { it.id })
            } finally {
                database.close()
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun `a broken file fails the import and keeps the previous content`(@TempDir dir: File) = runBlocking {
        val broken = File(dir, "broken.json").apply { writeText("{not json") }
        val database = DesktopDatabaseFactory.inMemory()
        try {
            seed(database)
            val controller = controller(database, FakeFileDialog(openResult = broken))

            val outcome = controller.import()

            assertTrue(outcome is TransferOutcome.Failed, "a broken file must fail, saw $outcome")
            assertTrue((outcome as TransferOutcome.Failed).message.isNotBlank())
            assertEquals(1, database.deviceDao().countActive(), "the failure must not touch the content")
        } finally {
            database.close()
        }
    }

    @Test
    fun `without an open mirror the controller refuses before the dialog`() = runBlocking {
        val dialog = FakeFileDialog()
        val controller = DatabaseTransferController(
            databaseProvider = { null },
            fileDialog = dialog,
            clock = { Instant.parse("2026-10-03T12:34:56Z") },
            ioDispatcher = Dispatchers.Unconfined,
        )

        assertTrue(!controller.available, "no mirror means no transfer")
        assertTrue(controller.export() is TransferOutcome.Failed, "export must refuse, saw")
        assertTrue(controller.import() is TransferOutcome.Failed, "import must refuse")
        assertNull(dialog.lastSuggestedName, "the dialog must not even open without a mirror")
    }

    private fun controller(database: DesktopDatabase, dialog: FakeFileDialog) =
        DatabaseTransferController(
            databaseProvider = { database },
            fileDialog = dialog,
            clock = { Instant.parse("2026-10-03T12:34:56Z") },
            ioDispatcher = Dispatchers.Unconfined,
        )

    private suspend fun seed(database: DesktopDatabase) {
        database.deviceDao().insert(device(id = 1L))
        database.relayRecordDao().insert(record())
    }

    private fun device(id: Long, name: String = "pixel") = DeviceEntity(
        id = id,
        userId = 1L,
        deviceName = name,
        displayName = name,
        createdAt = "2026-10-02T00:00:00Z",
        updatedAt = "2026-10-02T00:00:00Z",
    )

    private fun record() = RelayRecordEntity(
        id = 0L,
        userId = 1L,
        deviceId = 1L,
        eventId = "evt-1",
        recordType = "sms",
        sender = "10086",
        body = "123456",
        occurredAt = "2026-10-02T00:00:00Z",
        uploadedAt = "2026-10-02T00:00:00Z",
    )

    private class FakeFileDialog(
        private val saveResult: File? = null,
        private val openResult: File? = null,
    ) : DesktopFileDialog {

        var lastSuggestedName: String? = null
            private set

        override fun chooseSaveFile(suggestedName: String): File? {
            lastSuggestedName = suggestedName
            return saveResult
        }

        override fun chooseOpenFile(): File? = openResult
    }
}
