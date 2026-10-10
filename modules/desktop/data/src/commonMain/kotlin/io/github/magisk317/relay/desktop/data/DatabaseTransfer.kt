package io.github.magisk317.relay.desktop.data

import io.github.magisk317.relay.desktop.data.dao.RelayRecordDao
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigAuditLogEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigCommandEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigMirrorEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceEntity
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceBindCodeEntity
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceTokenEntity
import io.github.magisk317.relay.desktop.data.entity.RelayRecordEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Format marker every snapshot carries. A file without it is not ours - most
 * importantly, a Tauri-era `local-data.db` is a raw SQLite database, not a
 * snapshot, and per `docs/DESKTOP.md` §6 it is deliberately not
 * accepted here (the desktop track was never released, so no user holds one).
 */
internal const val DATABASE_SNAPSHOT_FORMAT = "xinyi-relay-desktop-database"

/**
 * Snapshot schema version. Bump when the row shape changes in a way an older
 * build could not read; [DatabaseTransfer.decode] refuses anything newer than
 * the build supports instead of guessing.
 */
internal const val DATABASE_SNAPSHOT_VERSION = 1

/**
 * Rejection reason for a file that is not a snapshot this build can read.
 *
 * Callers surface [message] to the user, so every rejection says what was
 * wrong: foreign format, newer version, missing table, malformed rows.
 */
class SnapshotFormatException(message: String) : IllegalArgumentException(message)

/**
 * Portable JSON image of the whole local store: every row of all seven tables,
 * stamped with the format marker, the schema version and the export time.
 *
 * Row identity is the entity's own primary key, so an import restores ids
 * (including the auto-generated ones) rather than re-allocating them. Unknown
 * keys are tolerated on read (an older build opening a newer file's extra
 * fields), missing tables are not (a replaced install must not silently drop a
 * table's rows on the floor).
 */
@Serializable
data class DatabaseSnapshot(
    val format: String = DATABASE_SNAPSHOT_FORMAT,
    val version: Int = DATABASE_SNAPSHOT_VERSION,
    val exportedAt: String = "",
    val devices: List<DeviceEntity> = emptyList(),
    val mirrors: List<DeviceConfigMirrorEntity> = emptyList(),
    val commands: List<DeviceConfigCommandEntity> = emptyList(),
    val auditLogs: List<DeviceConfigAuditLogEntity> = emptyList(),
    val records: List<RelayRecordEntity> = emptyList(),
    val bindCodes: List<LocalDeviceBindCodeEntity> = emptyList(),
    val tokens: List<LocalDeviceTokenEntity> = emptyList(),
)

/**
 * Export / import of the desktop store as a JSON snapshot.
 *
 * The snapshot format is this track's own (parity §5, `数据库导出 / 导入`): the
 * Tauri track answered the same job by copying the raw SQLite file, which is
 * why nothing here accepts one. Export reads every table through the DAOs;
 * import validates the envelope first and then replaces all rows inside a
 * single transaction - a rejected snapshot leaves the previous content exactly
 * as it was, never partially overwritten.
 *
 * File I/O stays out of this object on purpose: callers hand in or take back
 * the JSON text, which keeps the logic platform-independent and testable
 * against in-memory databases.
 */
object DatabaseTransfer {

    /** Marker a well-formed snapshot must carry. */
    const val FORMAT_ID: String = DATABASE_SNAPSHOT_FORMAT

    /** Newest snapshot schema this build can read. */
    const val SNAPSHOT_VERSION: Int = DATABASE_SNAPSHOT_VERSION

    private const val RECORD_PAGE_SIZE = 500

    private val requiredTables = listOf(
        "devices",
        "mirrors",
        "commands",
        "auditLogs",
        "records",
        "bindCodes",
        "tokens",
    )

    /**
     * Snapshot JSON settings.
     *
     * [encodeDefaults] must be explicit: on the current Kotlin/kotlinx-serialization
     * combination the implicit default is not reliably applied, so without it every
     * property that happens to hold its default (including the non-empty [format]
     * marker) is silently dropped from the encoded output. Verified by probe: only
     * this configuration emits all ten keys.
     */
    private val snapshotJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Serializes [snapshot] to the stable text form. */
    fun encode(snapshot: DatabaseSnapshot): String =
        snapshotJson.encodeToString(DatabaseSnapshot.serializer(), snapshot)

    /**
     * Parses and validates [text] into a snapshot.
     *
     * @throws SnapshotFormatException when the text is not JSON, carries a
     *   foreign format marker, a version newer than this build supports, a
     *   missing table, or rows that do not match the schema.
     */
    fun decode(text: String): DatabaseSnapshot {
        val root = try {
            snapshotJson.parseToJsonElement(text) as? JsonObject
        } catch (invalid: SerializationException) {
            null
        } ?: throw SnapshotFormatException("snapshot is not a JSON object")

        val format = root["format"]?.jsonPrimitive?.content
            ?: throw SnapshotFormatException("snapshot is missing the format marker")
        if (format != FORMAT_ID) {
            throw SnapshotFormatException(
                "unsupported snapshot format '$format'; expected '$FORMAT_ID' - " +
                    "a Tauri-era local-data.db is a raw SQLite file, not a supported import",
            )
        }

        val version = root["version"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw SnapshotFormatException("snapshot is missing a numeric version")
        if (version > SNAPSHOT_VERSION) {
            throw SnapshotFormatException(
                "snapshot version $version is newer than this build supports ($SNAPSHOT_VERSION)",
            )
        }

        val missing = requiredTables.filterNot { root.containsKey(it) }
        if (missing.isNotEmpty()) {
            throw SnapshotFormatException("snapshot is missing table(s): ${missing.joinToString(", ")}")
        }

        return try {
            snapshotJson.decodeFromString(DatabaseSnapshot.serializer(), text)
        } catch (invalid: SerializationException) {
            throw SnapshotFormatException("snapshot rows do not match the schema: ${invalid.message}")
        }
    }

    /** Reads every table of [database] into a snapshot stamped [exportedAt]. */
    suspend fun export(database: DesktopDatabase, exportedAt: String): DatabaseSnapshot {
        val devices = database.deviceDao()
        val configs = database.deviceConfigDao()
        val records = database.relayRecordDao()
        val localDevices = database.localDeviceDao()
        return DatabaseSnapshot(
            exportedAt = exportedAt,
            devices = devices.listAll(),
            mirrors = configs.listAllMirrors(),
            commands = configs.listAllCommands(),
            auditLogs = configs.listAllAuditLogs(),
            records = readAllRecords(records),
            bindCodes = localDevices.listAllBindCodes(),
            tokens = localDevices.listAllTokens(),
        )
    }

    /**
     * Replaces every row of [database] with [snapshot], atomically: the clears
     * and the writes share one transaction, so a rejection (for example the
     * pending-target partial unique index refusing a duplicated command)
     * rolls the whole import back and the previous content survives.
     */
    suspend fun import(database: DesktopDatabase, snapshot: DatabaseSnapshot) = database.inTransaction {
        val devices = database.deviceDao()
        val configs = database.deviceConfigDao()
        val records = database.relayRecordDao()
        val localDevices = database.localDeviceDao()

        devices.deleteAll()
        configs.deleteAllMirrors()
        configs.deleteAllCommands()
        configs.deleteAllAuditLogs()
        records.deleteAll()
        localDevices.deleteAllBindCodes()
        localDevices.deleteAllTokens()

        devices.upsertAll(snapshot.devices)
        snapshot.mirrors.forEach { configs.upsertMirror(it) }
        snapshot.commands.forEach { configs.insertCommand(it) }
        snapshot.auditLogs.forEach { configs.insertAuditLog(it) }
        records.upsertAll(snapshot.records)
        snapshot.bindCodes.forEach { localDevices.insertBindCode(it) }
        snapshot.tokens.forEach { localDevices.insertToken(it) }
    }

    /** [export] followed by [encode]. */
    suspend fun exportText(database: DesktopDatabase, exportedAt: String): String =
        encode(export(database, exportedAt))

    /** [decode] followed by [import]; returns the imported snapshot. */
    suspend fun importText(database: DesktopDatabase, text: String): DatabaseSnapshot {
        val snapshot = decode(text)
        import(database, snapshot)
        return snapshot
    }

    /**
     * Pages through the record table: the DAO has no unbounded read, and an
     * export must not silently truncate a store that outgrew one page.
     */
    private suspend fun readAllRecords(dao: RelayRecordDao): List<RelayRecordEntity> {
        val all = mutableListOf<RelayRecordEntity>()
        var offset = 0
        while (true) {
            val page = dao.listPage(RECORD_PAGE_SIZE, offset)
            all += page
            if (page.size < RECORD_PAGE_SIZE) return all
            offset += page.size
        }
    }
}
