package io.github.magisk317.relay.desktop.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
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

/**
 * Desktop local store. The schema mirrors the legacy Rust store exactly
 * so an existing `local-data.db` can be imported row-for-row.
 *
 * Two indexes cannot be expressed through Room annotations and are installed
 * from [PartialIndexes]:
 *
 * - `relay_records(device_id, event_id) WHERE event_id IS NOT NULL` (unique)
 *   — the record idempotency anchor.
 * - `device_config_commands(device_id, target_revision) WHERE status='pending'`
 *   (unique) — closes the duplicate-target gap the legacy store had.
 */
@Database(
    entities = [
        DeviceEntity::class,
        DeviceConfigMirrorEntity::class,
        DeviceConfigCommandEntity::class,
        DeviceConfigAuditLogEntity::class,
        RelayRecordEntity::class,
        LocalDeviceBindCodeEntity::class,
        LocalDeviceTokenEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@ConstructedBy(DesktopDatabaseConstructor::class)
abstract class DesktopDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    abstract fun deviceConfigDao(): DeviceConfigDao

    abstract fun relayRecordDao(): RelayRecordDao

    abstract fun localDeviceDao(): LocalDeviceDao

    companion object {
        const val DATABASE_NAME = "local-data-kmp.db"

        /**
         * File name of the legacy Rust store; kept only for import tooling.
         */
        const val LEGACY_DATABASE_NAME = "local-data.db"
    }
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object DesktopDatabaseConstructor : RoomDatabaseConstructor<DesktopDatabase>

/**
 * Indexes Room cannot generate. Applied before the database is handed out.
 */
internal object PartialIndexes {

    fun install(connection: androidx.sqlite.SQLiteConnection) {
        execSql(connection, INDEX_RECORD_EVENT_ID)
        execSql(connection, INDEX_PENDING_TARGET_REVISION)
    }

    private const val INDEX_RECORD_EVENT_ID = """
        CREATE UNIQUE INDEX IF NOT EXISTS relay_records_device_event_id_idx
            ON relay_records(device_id, event_id)
         WHERE event_id IS NOT NULL
    """

    private const val INDEX_PENDING_TARGET_REVISION = """
        CREATE UNIQUE INDEX IF NOT EXISTS device_config_commands_pending_target_idx
            ON device_config_commands(device_id, target_revision)
         WHERE status = 'pending'
    """
}

/**
 * Executes raw SQL against an open connection.
 *
 * `androidx.sqlite.execSQL` is declared for non-web targets only and is not
 * visible from common sources, so each target supplies its own binding.
 */
internal expect fun execSql(connection: androidx.sqlite.SQLiteConnection, sql: String)
