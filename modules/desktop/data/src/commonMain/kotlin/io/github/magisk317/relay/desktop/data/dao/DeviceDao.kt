package io.github.magisk317.relay.desktop.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import io.github.magisk317.relay.desktop.data.entity.DeviceEntity

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY id ASC")
    suspend fun listAll(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE id = :deviceId")
    suspend fun findById(deviceId: Long): DeviceEntity?

    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM devices")
    suspend fun nextDeviceId(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(device: DeviceEntity)

    @Update
    suspend fun update(device: DeviceEntity)

    /**
     * Upsert keyed on `id`. `created_at` is preserved on conflict, matching the
     * legacy Rust store (`sqlite_store.rs` upsert_devices).
     */
    @Transaction
    suspend fun upsert(device: DeviceEntity) {
        val existing = findById(device.id)
        if (existing == null) {
            insert(device)
        } else {
            update(device.copy(createdAt = existing.createdAt))
        }
    }

    @Transaction
    suspend fun upsertAll(devices: List<DeviceEntity>) {
        devices.forEach { upsert(it) }
    }

    @Query(
        """
        UPDATE devices
           SET app_version = :appVersion,
               local_addresses = :localAddresses,
               capabilities = :capabilities,
               last_seen_at = :now,
               updated_at = :now
         WHERE id = :deviceId AND revoked_at IS NULL
        """,
    )
    suspend fun applyHeartbeat(
        deviceId: Long,
        appVersion: String,
        localAddresses: String,
        capabilities: String,
        now: String,
    )

    @Query("UPDATE devices SET revoked_at = :now, updated_at = :now WHERE id = :deviceId AND revoked_at IS NULL")
    suspend fun revoke(deviceId: Long, now: String): Int

    @Query("UPDATE local_device_tokens SET revoked_at = :now WHERE device_id = :deviceId AND revoked_at IS NULL")
    suspend fun revokeToken(deviceId: Long, now: String): Int

    @Query("SELECT COUNT(*) FROM devices WHERE revoked_at IS NULL")
    suspend fun countActive(): Int
}
