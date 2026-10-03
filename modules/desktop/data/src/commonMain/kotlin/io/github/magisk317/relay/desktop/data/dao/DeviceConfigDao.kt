package io.github.magisk317.relay.desktop.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigAuditLogEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigCommandEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigMirrorEntity

@Dao
interface DeviceConfigDao {

    // ---------------------------------------------------------------- mirror

    @Query("SELECT * FROM device_config_mirrors WHERE device_id = :deviceId")
    suspend fun findMirror(deviceId: Long): DeviceConfigMirrorEntity?

    @Upsert
    suspend fun upsertMirror(mirror: DeviceConfigMirrorEntity)

    @Query("DELETE FROM device_config_mirrors WHERE device_id = :deviceId")
    suspend fun deleteMirror(deviceId: Long)

    // --------------------------------------------------------------- command

    @Query(
        """
        SELECT * FROM device_config_commands
         WHERE device_id = :deviceId AND status = 'pending'
         ORDER BY created_at ASC, id ASC
        """,
    )
    suspend fun listPendingCommands(deviceId: Long): List<DeviceConfigCommandEntity>

    @Query("SELECT * FROM device_config_commands WHERE id = :commandId AND device_id = :deviceId")
    suspend fun findCommand(deviceId: Long, commandId: Long): DeviceConfigCommandEntity?

    @Query(
        """
        SELECT COALESCE(MAX(target_revision), 0)
          FROM device_config_commands
         WHERE device_id = :deviceId AND status = 'pending'
        """,
    )
    suspend fun latestPendingTargetRevision(deviceId: Long): Long

    @Query(
        """
        SELECT COALESCE(MAX(target_revision), 0)
          FROM device_config_commands
         WHERE device_id = :deviceId
        """,
    )
    suspend fun latestTargetRevision(deviceId: Long): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommand(command: DeviceConfigCommandEntity): Long

    @Update
    suspend fun updateCommand(command: DeviceConfigCommandEntity)

    @Query(
        """
        UPDATE device_config_commands
           SET status = :status,
               failure_reason = :failureReason,
               updated_at = :now,
               applied_at = :appliedAt
         WHERE id = :commandId AND device_id = :deviceId AND status = 'pending'
        """,
    )
    suspend fun ackCommand(
        deviceId: Long,
        commandId: Long,
        status: String,
        failureReason: String?,
        now: String,
        appliedAt: String?,
    ): Int

    @Query("DELETE FROM device_config_commands WHERE device_id = :deviceId AND status = 'pending'")
    suspend fun clearPendingCommands(deviceId: Long)

    /**
     * Drops queued commands whose base revision is behind the mirror revision.
     * The legacy Rust store leaves these rows in place forever, which lets a
     * dead queue accumulate after a remote revision jump.
     */
    @Query(
        """
        DELETE FROM device_config_commands
         WHERE device_id = :deviceId
           AND status = 'pending'
           AND base_revision < :mirrorRevision
        """,
    )
    suspend fun dropStalePendingCommands(deviceId: Long, mirrorRevision: Long): Int

    // ------------------------------------------------------------ audit logs

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(log: DeviceConfigAuditLogEntity)

    @Query(
        """
        SELECT * FROM device_config_audit_logs
         WHERE device_id = :deviceId
         ORDER BY id DESC
         LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listAuditLogs(deviceId: Long, limit: Int, offset: Int): List<DeviceConfigAuditLogEntity>

    @Transaction
    suspend fun replacePendingCommands(
        deviceId: Long,
        commands: List<DeviceConfigCommandEntity>,
    ) {
        clearPendingCommands(deviceId)
        commands.forEach { insertCommand(it) }
    }

    // -------------------------------------------- full-table export / import
    //
    // The snapshot export needs every row of the three config tables (not just
    // the pending queue, the mirrors of live devices, or the first page of the
    // audit log), and the import needs to clear each table before re-writing.
    // Kept next to the per-device queries above so the two access shapes stay
    // visibly different in call sites.

    @Query("SELECT * FROM device_config_mirrors ORDER BY device_id ASC")
    suspend fun listAllMirrors(): List<DeviceConfigMirrorEntity>

    @Query("DELETE FROM device_config_mirrors")
    suspend fun deleteAllMirrors()

    @Query("SELECT * FROM device_config_commands ORDER BY id ASC")
    suspend fun listAllCommands(): List<DeviceConfigCommandEntity>

    @Query("DELETE FROM device_config_commands")
    suspend fun deleteAllCommands()

    @Query("SELECT * FROM device_config_audit_logs ORDER BY id ASC")
    suspend fun listAllAuditLogs(): List<DeviceConfigAuditLogEntity>

    @Query("DELETE FROM device_config_audit_logs")
    suspend fun deleteAllAuditLogs()
}
