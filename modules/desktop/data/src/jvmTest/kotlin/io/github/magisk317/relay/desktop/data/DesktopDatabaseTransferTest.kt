package io.github.magisk317.relay.desktop.data

import io.github.magisk317.relay.desktop.data.entity.DeviceConfigAuditLogEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigCommandEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigMirrorEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceEntity
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceBindCodeEntity
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceTokenEntity
import io.github.magisk317.relay.desktop.data.entity.RelayRecordEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Contract of the JSON snapshot format (parity §5, `数据库导出 / 导入`).
 *
 * The invariants that matter beyond a plain round trip: the envelope is
 * validated before any row moves, a rejected import leaves the previous
 * content untouched, and the Tauri-era raw SQLite file is refused rather than
 * half-read.
 */
class DesktopDatabaseTransferTest {

    @Test
    fun `every table round trips through export and import`() = runBlocking {
        val source = DesktopDatabaseFactory.inMemory()
        try {
            seed(source)
            val snapshot = DatabaseTransfer.export(source, exportedAt = "2026-10-03T00:00:00Z")
            val text = DatabaseTransfer.encode(snapshot)

            val target = DesktopDatabaseFactory.inMemory()
            try {
                val imported = DatabaseTransfer.importText(target, text)
                assertEquals("2026-10-03T00:00:00Z", imported.exportedAt)

                val reExported = DatabaseTransfer.export(target, exportedAt = imported.exportedAt)
                assertEquals(snapshot.normalized(), reExported.normalized())
            } finally {
                target.close()
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun `export carries the format and version markers`() = runBlocking {
        val database = DesktopDatabaseFactory.inMemory()
        try {
            val snapshot = DatabaseTransfer.export(database, exportedAt = "2026-10-03T00:00:00Z")
            assertEquals(DatabaseTransfer.FORMAT_ID, snapshot.format)
            assertEquals(DatabaseTransfer.SNAPSHOT_VERSION, snapshot.version)

            val decoded = DatabaseTransfer.decode(DatabaseTransfer.encode(snapshot))
            assertEquals(snapshot.normalized(), decoded.normalized())
        } finally {
            database.close()
        }
    }

    @Test
    fun `import replaces every existing row`() = runBlocking {
        val database = DesktopDatabaseFactory.inMemory()
        try {
            seed(database)
            DatabaseTransfer.import(
                database,
                DatabaseSnapshot(
                    exportedAt = "2026-10-03T01:00:00Z",
                    devices = listOf(
                        DeviceEntity(
                            id = 9L,
                            userId = 2L,
                            deviceName = "other",
                            createdAt = "2026-10-03T00:00:00Z",
                            updatedAt = "2026-10-03T00:00:00Z",
                        ),
                    ),
                ),
            )

            assertEquals(listOf(9L), database.deviceDao().listAll().map { it.id })
            assertEquals(0, database.relayRecordDao().listPage(10, 0).size)
            assertEquals(0, database.deviceConfigDao().listAllMirrors().size)
            assertEquals(0, database.deviceConfigDao().listAllCommands().size)
            assertEquals(0, database.localDeviceDao().countTokens())
            assertEquals(0, database.localDeviceDao().countBindCodes())
        } finally {
            database.close()
        }
    }

    @Test
    fun `a foreign format marker is rejected`() {
        val text = DatabaseTransfer.encode(DatabaseSnapshot(exportedAt = "2026-10-03T00:00:00Z"))
            .replace(DatabaseTransfer.FORMAT_ID, "some-other-app")
        val failure = assertFailsWith<SnapshotFormatException> { DatabaseTransfer.decode(text) }
        assertTrue(
            failure.message!!.contains("unsupported snapshot format"),
            "rejection must name the format problem, saw: ${failure.message}",
        )
    }

    @Test
    fun `a newer snapshot version is rejected`() {
        val text = DatabaseTransfer.encode(DatabaseSnapshot(exportedAt = "2026-10-03T00:00:00Z"))
            .replace(
                "\"version\": ${DatabaseTransfer.SNAPSHOT_VERSION}",
                "\"version\": ${DatabaseTransfer.SNAPSHOT_VERSION + 1}",
            )
        val failure = assertFailsWith<SnapshotFormatException> { DatabaseTransfer.decode(text) }
        assertTrue(
            failure.message!!.contains("newer than this build supports"),
            "rejection must name the version problem, saw: ${failure.message}",
        )
    }

    @Test
    fun `a snapshot without a numeric version is rejected`() {
        val text = """
            {"format":"${DatabaseTransfer.FORMAT_ID}",
             "exportedAt":"2026-10-03T00:00:00Z","devices":[]}
        """.trimIndent()
        assertFailsWith<SnapshotFormatException> { DatabaseTransfer.decode(text) }
    }

    @Test
    fun `malformed json is rejected`() {
        val failure = assertFailsWith<SnapshotFormatException> { DatabaseTransfer.decode("{not json") }
        assertTrue(
            failure.message!!.contains("not a JSON object"),
            "rejection must say the file is not JSON, saw: ${failure.message}",
        )
    }

    @Test
    fun `a snapshot missing a table is rejected`() {
        val text = """
            {"format":"${DatabaseTransfer.FORMAT_ID}","version":${DatabaseTransfer.SNAPSHOT_VERSION},
             "exportedAt":"2026-10-03T00:00:00Z","devices":[]}
        """.trimIndent()
        val failure = assertFailsWith<SnapshotFormatException> { DatabaseTransfer.decode(text) }
        assertTrue(
            failure.message!!.contains("records"),
            "rejection must name the missing table, saw: ${failure.message}",
        )
    }

    @Test
    fun `a legacy tauri sqlite file is rejected as a foreign format`() {
        // The Tauri track exported by copying local-data.db; its first bytes are
        // the SQLite magic header, never a JSON envelope.
        val failure = assertFailsWith<SnapshotFormatException> {
            DatabaseTransfer.decode("SQLite format 3\u0000 not json at all")
        }
        assertTrue(
            failure.message!!.contains("not a JSON object"),
            "a raw database file must be refused as not-JSON, saw: ${failure.message}",
        )
    }

    @Test
    fun `unknown snapshot keys are tolerated`() = runBlocking {
        val database = DesktopDatabaseFactory.inMemory()
        try {
            seed(database)
            val text = DatabaseTransfer.encode(DatabaseTransfer.export(database, "2026-10-03T00:00:00Z"))
                .replaceFirst("{", "{\n  \"futureField\": 42,")
            val decoded = DatabaseTransfer.decode(text)
            assertEquals(1, decoded.devices.size)
            assertEquals(2, decoded.records.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `a rejected import rolls back and keeps the previous content`() = runBlocking {
        val database = DesktopDatabaseFactory.inMemory()
        try {
            seed(database)
            // Two pending commands for the same device and target revision trip
            // the partial unique index, so the import must fail as a whole.
            val broken = DatabaseSnapshot(
                exportedAt = "2026-10-03T01:00:00Z",
                commands = listOf(
                    pendingCommand(deviceId = 5L, targetRevision = 1L),
                    pendingCommand(deviceId = 5L, targetRevision = 1L),
                ),
            )
            assertFailsWith<Exception> { DatabaseTransfer.import(database, broken) }

            assertEquals(1, database.deviceDao().countActive(), "the rollback must restore the devices")
            assertEquals(2, database.relayRecordDao().listPage(10, 0).size, "and the records")
            assertEquals(1, database.deviceConfigDao().listAllMirrors().size, "and the mirrors")
        } finally {
            database.close()
        }
    }

    private suspend fun seed(database: DesktopDatabase) {
        val devices = database.deviceDao()
        val configs = database.deviceConfigDao()
        val records = database.relayRecordDao()
        val localDevices = database.localDeviceDao()

        devices.insert(
            DeviceEntity(
                id = 1L,
                userId = 1L,
                deviceName = "pixel",
                displayName = "Pixel",
                createdAt = "2026-10-02T00:00:00Z",
                updatedAt = "2026-10-02T00:00:00Z",
            ),
        )
        configs.upsertMirror(
            DeviceConfigMirrorEntity(
                deviceId = 1L,
                revision = 3L,
                snapshot = "{\"theme\":\"dark\"}",
                updatedAt = "2026-10-02T00:00:00Z",
            ),
        )
        val commandId = configs.insertCommand(
            pendingCommand(deviceId = 1L, targetRevision = 4L),
        )
        configs.insertAuditLog(
            DeviceConfigAuditLogEntity(
                deviceId = 1L,
                commandId = commandId,
                revision = 3L,
                eventType = "command.queued",
                summary = "rename",
                createdAt = "2026-10-02T00:00:00Z",
            ),
        )
        records.insert(record(eventId = "evt-1", body = "first"))
        records.insert(record(eventId = null, body = "second"))
        localDevices.insertBindCode(
            LocalDeviceBindCodeEntity(
                codeHash = "hash-1",
                expiresAt = "2026-10-03T01:00:00Z",
                usedAt = "2026-10-03T00:30:00Z",
            ),
        )
        localDevices.insertToken(
            LocalDeviceTokenEntity(deviceId = 1L, tokenHash = "token-1"),
        )
    }

    private fun pendingCommand(deviceId: Long, targetRevision: Long): DeviceConfigCommandEntity =
        DeviceConfigCommandEntity(
            deviceId = deviceId,
            baseRevision = 0L,
            targetRevision = targetRevision,
            status = "pending",
            createdAt = "2026-10-02T00:00:00Z",
            updatedAt = "2026-10-02T00:00:00Z",
        )

    private fun record(eventId: String?, body: String): RelayRecordEntity =
        RelayRecordEntity(
            id = 0L,
            userId = 1L,
            deviceId = 1L,
            eventId = eventId,
            recordType = "sms",
            sender = "10086",
            body = body,
            occurredAt = "2026-10-02T00:00:00Z",
            uploadedAt = "2026-10-02T00:00:00Z",
        )

    /** Compares snapshots ignoring the export stamp, which is free-form. */
    private fun DatabaseSnapshot.normalized(): DatabaseSnapshot = copy(exportedAt = "")
}
