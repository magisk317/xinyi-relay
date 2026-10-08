package io.github.magisk317.relay.desktop.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.magisk317.relay.desktop.data.dao.DeviceConfigDao
import io.github.magisk317.relay.desktop.data.dao.DeviceDao
import io.github.magisk317.relay.desktop.data.dao.LocalDeviceDao
import io.github.magisk317.relay.desktop.data.dao.RelayRecordDao
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigAuditLogEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigCommandEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigMirrorEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceEntity
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceBindCodeEntity
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceTokenEntity
import io.github.magisk317.relay.desktop.data.entity.RelayRecordEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One-way importer for databases written by the Rust/Tauri desktop client.
 *
 * The legacy file is opened read-only and never rewritten: the Room database is
 * created from scratch and the rows are copied over. Callers should wrap the
 * call in a Room transaction so a failed import rolls back instead of leaving a
 * half-migrated store behind.
 *
 * Semantic adjustments applied during the copy:
 *
 * - Pending commands sharing one `(device_id, target_revision)` pair are
 *   collapsed: the first row keeps its status, later duplicates are recorded as
 *   `failed` with `stale_base_revision`. The new schema carries the partial
 *   unique index the legacy schema lacked.
 * - Ids and timestamps are copied verbatim so ordering of existing rows is
 *   preserved.
 */
class LegacyDatabaseImporter(
    private val devices: DeviceDao,
    private val configs: DeviceConfigDao,
    private val records: RelayRecordDao,
    private val localDevices: LocalDeviceDao,
) {

    private val writeGuard = Mutex()

    data class Report(
        val devices: Int = 0,
        val mirrors: Int = 0,
        val commands: Int = 0,
        val auditLogs: Int = 0,
        val records: Int = 0,
        val bindCodes: Int = 0,
        val tokens: Int = 0,
        val foldedCommands: Int = 0,
    )

    suspend fun import(legacyPath: String): Report {
        val connection = BundledSQLiteDriver().open(legacyPath)
        return try {
            requireTables(connection)
            writeGuard.withLock {
                val devices = importDevices(connection)
                val mirrors = importMirrors(connection)
                val commandImport = importCommands(connection)
                val auditLogs = importAuditLogs(connection)
                val records = importRecords(connection)
                val bindCodes = importBindCodes(connection)
                val tokens = importTokens(connection)
                Report(
                    devices = devices,
                    mirrors = mirrors,
                    commands = commandImport.total,
                    auditLogs = auditLogs,
                    records = records,
                    bindCodes = bindCodes,
                    tokens = tokens,
                    foldedCommands = commandImport.folded,
                )
            }
        } finally {
            connection.close()
        }
    }

    private fun requireTables(connection: SQLiteConnection) {
        val existing = mutableSetOf<String>()
        connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { statement ->
            while (statement.step()) {
                existing.add(statement.getText(0))
            }
        }
        REQUIRED_TABLES.forEach { table ->
            require(table in existing) { "legacy database is missing table $table" }
        }
    }

    private suspend fun importDevices(connection: SQLiteConnection): Int {
        var count = 0
        connection.prepare(
            """
            SELECT id, user_id, device_name, device_model, platform, app_version, display_name,
                   enabled, revoked_at, last_seen_at, local_addresses, capabilities,
                   created_at, updated_at
              FROM devices
            """.trimIndent(),
        ).use { statement ->
            while (statement.step()) {
                devices.insert(
                    DeviceEntity(
                        id = statement.getLong(0),
                        userId = statement.getLong(1),
                        deviceName = statement.getText(2),
                        deviceModel = statement.getText(3),
                        platform = statement.getText(4),
                        appVersion = statement.getText(5),
                        displayName = statement.getText(6),
                        enabled = statement.getLong(7) != 0L,
                        revokedAt = statement.getTextOrNull(8),
                        lastSeenAt = statement.getTextOrNull(9),
                        localAddresses = statement.getText(10),
                        capabilities = statement.getText(11),
                        createdAt = statement.getText(12),
                        updatedAt = statement.getText(13),
                    ),
                )
                count += 1
            }
        }
        return count
    }

    private suspend fun importMirrors(connection: SQLiteConnection): Int {
        var count = 0
        connection.prepare(
            "SELECT device_id, revision, snapshot, updated_at FROM device_config_mirrors",
        ).use { statement ->
            while (statement.step()) {
                configs.upsertMirror(
                    DeviceConfigMirrorEntity(
                        deviceId = statement.getLong(0),
                        revision = statement.getLong(1),
                        snapshot = statement.getText(2),
                        updatedAt = statement.getTextOrNull(3),
                    ),
                )
                count += 1
            }
        }
        return count
    }

    private data class CommandImport(val total: Int, val folded: Int)

    private suspend fun importCommands(connection: SQLiteConnection): CommandImport {
        val rows = mutableListOf<DeviceConfigCommandEntity>()
        connection.prepare(
            """
            SELECT id, device_id, base_revision, target_revision, mutation, summary, actor_type,
                   actor_id, status, failure_reason, created_at, updated_at, applied_at
              FROM device_config_commands
             ORDER BY device_id ASC, id ASC
            """.trimIndent(),
        ).use { statement ->
            while (statement.step()) {
                rows.add(
                    DeviceConfigCommandEntity(
                        id = statement.getLong(0),
                        deviceId = statement.getLong(1),
                        baseRevision = statement.getLong(2),
                        targetRevision = statement.getLong(3),
                        mutation = statement.getText(4),
                        summary = statement.getText(5),
                        actorType = statement.getText(6),
                        actorId = statement.getLong(7),
                        status = statement.getText(8),
                        failureReason = statement.getTextOrNull(9),
                        createdAt = statement.getText(10),
                        updatedAt = statement.getText(11),
                        appliedAt = statement.getTextOrNull(12),
                    ),
                )
            }
        }

        // Non-pending rows are unconstrained; pending rows must be unique per
        // (device_id, target_revision) under the new partial index.
        var folded = 0
        val seenPending = mutableSetOf<Pair<Long, Long>>()
        rows.forEach { row ->
            if (row.status != STATUS_PENDING) {
                configs.insertCommand(row)
                return@forEach
            }
            if (seenPending.add(row.deviceId to row.targetRevision)) {
                configs.insertCommand(row)
            } else {
                configs.insertCommand(
                    row.copy(
                        status = STATUS_FAILED,
                        failureReason = FAILURE_REASON_STALE_BASE_REVISION,
                    ),
                )
                folded += 1
            }
        }
        return CommandImport(total = rows.size, folded = folded)
    }

    private suspend fun importAuditLogs(connection: SQLiteConnection): Int {
        var count = 0
        connection.prepare(
            """
            SELECT id, device_id, command_id, revision, event_type, actor_type, actor_id,
                   summary, created_at
              FROM device_config_audit_logs
             ORDER BY id ASC
            """.trimIndent(),
        ).use { statement ->
            while (statement.step()) {
                configs.insertAuditLog(
                    DeviceConfigAuditLogEntity(
                        id = statement.getLong(0),
                        deviceId = statement.getLong(1),
                        commandId = if (statement.isNull(2)) null else statement.getLong(2),
                        revision = statement.getLong(3),
                        eventType = statement.getText(4),
                        actorType = statement.getText(5),
                        actorId = statement.getLong(6),
                        summary = statement.getText(7),
                        createdAt = statement.getText(8),
                    ),
                )
                count += 1
            }
        }
        return count
    }

    private suspend fun importRecords(connection: SQLiteConnection): Int {
        var count = 0
        connection.prepare(
            """
            SELECT id, user_id, device_id, event_id, record_type, sender, body, sms_code,
                   package_name, msg_type, call_type, occurred_at, uploaded_at, metadata
              FROM relay_records
             ORDER BY id ASC
            """.trimIndent(),
        ).use { statement ->
            while (statement.step()) {
                records.insert(
                    RelayRecordEntity(
                        id = statement.getLong(0),
                        userId = statement.getLong(1),
                        deviceId = statement.getLong(2),
                        eventId = statement.getTextOrNull(3),
                        recordType = statement.getText(4),
                        sender = statement.getText(5),
                        body = statement.getText(6),
                        smsCode = statement.getText(7),
                        packageName = statement.getText(8),
                        msgType = statement.getLong(9),
                        callType = statement.getLong(10),
                        occurredAt = statement.getText(11),
                        uploadedAt = statement.getText(12),
                        metadata = statement.getText(13),
                    ),
                )
                count += 1
            }
        }
        return count
    }

    private suspend fun importBindCodes(connection: SQLiteConnection): Int {
        var count = 0
        connection.prepare(
            "SELECT code_hash, expires_at, used_at FROM local_device_bind_codes",
        ).use { statement ->
            while (statement.step()) {
                localDevices.insertBindCode(
                    LocalDeviceBindCodeEntity(
                        codeHash = statement.getText(0),
                        expiresAt = statement.getText(1),
                        usedAt = statement.getTextOrNull(2),
                    ),
                )
                count += 1
            }
        }
        return count
    }

    private suspend fun importTokens(connection: SQLiteConnection): Int {
        var count = 0
        connection.prepare(
            "SELECT device_id, token_hash, revoked_at FROM local_device_tokens",
        ).use { statement ->
            while (statement.step()) {
                localDevices.insertToken(
                    LocalDeviceTokenEntity(
                        deviceId = statement.getLong(0),
                        tokenHash = statement.getText(1),
                        revokedAt = statement.getTextOrNull(2),
                    ),
                )
                count += 1
            }
        }
        return count
    }

    private fun SQLiteStatement.getTextOrNull(index: Int): String? =
        if (isNull(index)) null else getText(index)

    private inline fun <T : SQLiteStatement, R> T.use(block: (T) -> R): R {
        try {
            return block(this)
        } finally {
            close()
        }
    }

    companion object {
        private val REQUIRED_TABLES = listOf(
            "devices",
            "device_config_mirrors",
            "device_config_commands",
            "device_config_audit_logs",
            "relay_records",
            "local_device_bind_codes",
            "local_device_tokens",
        )

        const val STATUS_PENDING = "pending"
        const val STATUS_FAILED = "failed"
        const val FAILURE_REASON_STALE_BASE_REVISION = "stale_base_revision"

        /**
         * Creates an empty database carrying the exact schema the Rust client
         * wrote. Used by migration tests to build legacy fixtures.
         */
        fun createEmptyLegacyDatabase(path: String) {
            val connection = BundledSQLiteDriver().open(path)
            try {
                // `SQLiteConnection.execSQL` runs a single statement, so the
                // multi-statement `SCHEMA` must be split and executed one by one;
                // passing the whole blob only creates the first table.
                SCHEMA.split(";")
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { statement -> connection.execSQL(statement) }
            } finally {
                connection.close()
            }
        }

        /**
         * Verbatim copy of `sqlite_store.rs::migrate`, including the defaults
         * that make the fixture indistinguishable from a real legacy file.
         */
        val SCHEMA: String = """
            CREATE TABLE IF NOT EXISTS device_config_commands (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id INTEGER NOT NULL,
                base_revision INTEGER NOT NULL,
                target_revision INTEGER NOT NULL,
                mutation TEXT NOT NULL DEFAULT '{}',
                summary TEXT NOT NULL DEFAULT '',
                actor_type TEXT NOT NULL DEFAULT 'desktop',
                actor_id INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'pending',
                failure_reason TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                applied_at TEXT
            );
            CREATE TABLE IF NOT EXISTS device_config_mirrors (
                device_id INTEGER PRIMARY KEY,
                revision INTEGER NOT NULL DEFAULT 0,
                snapshot TEXT NOT NULL DEFAULT '{}',
                updated_at TEXT
            );
            CREATE TABLE IF NOT EXISTS device_config_audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id INTEGER NOT NULL,
                command_id INTEGER,
                revision INTEGER NOT NULL DEFAULT 0,
                event_type TEXT NOT NULL DEFAULT '',
                actor_type TEXT NOT NULL DEFAULT 'desktop',
                actor_id INTEGER NOT NULL DEFAULT 0,
                summary TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE TABLE IF NOT EXISTS devices (
                id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL DEFAULT 0,
                device_name TEXT NOT NULL DEFAULT '',
                device_model TEXT NOT NULL DEFAULT '',
                platform TEXT NOT NULL DEFAULT 'android',
                app_version TEXT NOT NULL DEFAULT '',
                display_name TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL DEFAULT 1,
                revoked_at TEXT,
                last_seen_at TEXT,
                local_addresses TEXT NOT NULL DEFAULT '[]',
                capabilities TEXT NOT NULL DEFAULT '{}',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE TABLE IF NOT EXISTS relay_records (
                id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL DEFAULT 0,
                device_id INTEGER NOT NULL DEFAULT 0,
                event_id TEXT,
                record_type TEXT NOT NULL DEFAULT '',
                sender TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                sms_code TEXT NOT NULL DEFAULT '',
                package_name TEXT NOT NULL DEFAULT '',
                msg_type INTEGER NOT NULL DEFAULT 0,
                call_type INTEGER NOT NULL DEFAULT 0,
                occurred_at TEXT NOT NULL,
                uploaded_at TEXT NOT NULL DEFAULT (datetime('now')),
                metadata TEXT NOT NULL DEFAULT '{}'
            );
            CREATE UNIQUE INDEX IF NOT EXISTS relay_records_device_event_id_idx
                ON relay_records(device_id, event_id)
             WHERE event_id IS NOT NULL;
            CREATE TABLE IF NOT EXISTS local_device_bind_codes (
                code_hash TEXT PRIMARY KEY,
                expires_at TEXT NOT NULL,
                used_at TEXT
            );
            CREATE TABLE IF NOT EXISTS local_device_tokens (
                device_id INTEGER PRIMARY KEY,
                token_hash TEXT NOT NULL UNIQUE,
                revoked_at TEXT
            );
        """.trimIndent()
    }
}
