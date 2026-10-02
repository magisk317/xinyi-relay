package io.github.magisk317.relay.desktop.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.magisk317.relay.desktop.data.LegacyDatabaseImporter
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Migration contract for the Rust/Tauri desktop database.
 *
 * The importer must preserve every row it can, never rewrite the legacy file,
 * and fold the pending-command duplicates the new partial unique index rejects.
 */
class LegacyDatabaseImporterTest {

    @Test
    fun `imports a legacy database row-for-row`() = runBlocking {
        val legacy = tempFile("legacy")
        LegacyDatabaseImporter.createEmptyLegacyDatabase(legacy.absolutePath)
        seedLegacy(legacy.absolutePath)

        val database = DesktopDatabaseFactory.inMemory()
        try {
            val report = database.inTransaction {
                importer(database).import(legacy.absolutePath)
            }

            assertEquals(1, report.devices)
            assertEquals(1, report.mirrors)
            assertEquals(3, report.commands)
            assertEquals(1, report.auditLogs)
            assertEquals(2, report.records)
            assertEquals(1, report.bindCodes)
            assertEquals(1, report.tokens)
            assertEquals(1, report.foldedCommands)

            assertEquals(1, database.deviceDao().listAll().size)
            assertEquals(2, database.relayRecordDao().listPage(10, 0).size)
            assertEquals(5L, database.deviceConfigDao().findMirror(1L)?.revision)
            assertEquals(
                "2026-10-01T00:00:00Z",
                database.deviceDao().findById(1L)?.createdAt,
                "created_at must survive the import unchanged",
            )

            val pending = database.deviceConfigDao().listPendingCommands(1L)
            assertEquals(1, pending.size, "duplicate pending target revisions must be folded")
            val folded = database.deviceConfigDao().findCommand(1L, 3L)
            assertEquals("failed", folded?.status)
            assertEquals("stale_base_revision", folded?.failureReason)
        } finally {
            database.close()
        }
    }

    @Test
    fun `rejects a file that is not a legacy store`() = runBlocking {
        val notLegacy = tempFile("not-legacy")
        val connection = BundledSQLiteDriver().open(notLegacy.absolutePath)
        try {
            connection.execSQL("CREATE TABLE unrelated (id INTEGER PRIMARY KEY)")
        } finally {
            connection.close()
        }

        val database = DesktopDatabaseFactory.inMemory()
        try {
            var message = ""
            try {
                importer(database).import(notLegacy.absolutePath)
            } catch (error: IllegalArgumentException) {
                message = error.message.orEmpty()
            }
            assertTrue(message.contains("missing table"), "expected a schema rejection, got: $message")
            assertEquals(0, database.deviceDao().listAll().size)
        } finally {
            database.close()
        }
    }

    private fun importer(database: DesktopDatabase): LegacyDatabaseImporter =
        LegacyDatabaseImporter(
            devices = database.deviceDao(),
            configs = database.deviceConfigDao(),
            records = database.relayRecordDao(),
            localDevices = database.localDeviceDao(),
        )

    private fun seedLegacy(path: String) {
        val connection = BundledSQLiteDriver().open(path)
        try {
            // `androidx.sqlite`'s `execSQL` runs a single statement, so the
            // multi-row fixture must be split on ';' and executed one by one;
            // passing the whole blob only runs the first INSERT.
            Fixture.SEED.split(";")
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { statement -> connection.execSQL(statement) }
        } finally {
            connection.close()
        }
    }

    private object Fixture {
        val SEED: String = """
            INSERT INTO devices (id, user_id, device_name, device_model, platform, app_version,
                                 display_name, enabled, local_addresses, capabilities,
                                 created_at, updated_at)
            VALUES (1, 1, 'pixel', 'Pixel 8', 'android', '0.2.5', 'pixel', 1, '[]', '{}',
                    '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z');

            INSERT INTO device_config_mirrors (device_id, revision, snapshot, updated_at)
            VALUES (1, 5, '{"a":1}', '2026-10-01T00:00:00Z');

            INSERT INTO device_config_commands (device_id, base_revision, target_revision,
                                                mutation, summary, status, created_at, updated_at)
            VALUES (1, 5, 6, '{}', 'applied', 'applied', '2026-10-01T00:00:01Z', '2026-10-01T00:00:01Z');

            INSERT INTO device_config_commands (device_id, base_revision, target_revision,
                                                mutation, summary, status, created_at, updated_at)
            VALUES (1, 4, 5, '{}', 'first', 'pending', '2026-10-01T00:00:02Z', '2026-10-01T00:00:02Z');

            INSERT INTO device_config_commands (device_id, base_revision, target_revision,
                                                mutation, summary, status, created_at, updated_at)
            VALUES (1, 4, 5, '{}', 'duplicate', 'pending', '2026-10-01T00:00:03Z', '2026-10-01T00:00:03Z');

            INSERT INTO device_config_audit_logs (device_id, command_id, revision, event_type, summary, created_at)
            VALUES (1, 1, 6, 'command.applied', 'applied', '2026-10-01T00:00:01Z');

            INSERT INTO relay_records (id, user_id, device_id, event_id, record_type, sender, body,
                                       occurred_at, uploaded_at, metadata)
            VALUES (1, 1, 1, 'evt-1', 'sms', '10086', 'hello', '2026-10-01T00:00:04Z',
                    '2026-10-01T00:00:04Z', '{}');

            INSERT INTO relay_records (id, user_id, device_id, event_id, record_type, sender, body,
                                       occurred_at, uploaded_at, metadata)
            VALUES (2, 1, 1, 'evt-2', 'sms', '10086', 'world', '2026-10-01T00:00:05Z',
                    '2026-10-01T00:00:05Z', '{}');

            INSERT INTO local_device_bind_codes (code_hash, expires_at, used_at)
            VALUES ('hash', '2026-10-01T00:10:00Z', NULL);

            INSERT INTO local_device_tokens (device_id, token_hash) VALUES (1, 'token-hash');
        """.trimIndent()
    }

    private fun tempFile(prefix: String): File =
        File.createTempFile(prefix, ".db").also { it.deleteOnExit() }
}
