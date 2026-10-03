package io.github.magisk317.relay.desktop.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.magisk317.relay.desktop.data.entity.RelayRecordEntity

@Dao
interface RelayRecordDao {

    @Query("SELECT * FROM relay_records WHERE id = :id")
    suspend fun findById(id: Long): RelayRecordEntity?

    @Query(
        """
        SELECT * FROM relay_records
         ORDER BY occurred_at DESC, id DESC
         LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listPage(limit: Int, offset: Int): List<RelayRecordEntity>

    @Query(
        """
        SELECT * FROM relay_records
         WHERE device_id = :deviceId
         ORDER BY occurred_at DESC, id DESC
         LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listPageForDevice(deviceId: Long, limit: Int, offset: Int): List<RelayRecordEntity>

    @Query("SELECT event_id FROM relay_records WHERE device_id = :deviceId AND event_id IS NOT NULL")
    suspend fun listEventIds(deviceId: Long): List<String>

    @Query("SELECT id, event_id FROM relay_records WHERE device_id = :deviceId")
    suspend fun listIdEventIdPairs(deviceId: Long): List<RelayRecordIdEvent>

    @Query("DELETE FROM relay_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: RelayRecordEntity): Long

    @Update
    suspend fun update(record: RelayRecordEntity)

    @Query("DELETE FROM relay_records WHERE device_id = :deviceId")
    suspend fun deleteForDevice(deviceId: Long)

    /**
     * Upsert keyed on `id`. `created_at` does not exist on this table; the
     * upload timestamp is preserved on conflict, matching the legacy store.
     */
    @Transaction
    suspend fun upsert(record: RelayRecordEntity) {
        val existing = if (record.id == 0L) null else findById(record.id)
        if (existing == null) {
            insert(record)
        } else {
            update(record.copy(uploadedAt = existing.uploadedAt))
        }
    }

    @Transaction
    suspend fun upsertAll(records: List<RelayRecordEntity>) {
        records.forEach { upsert(it) }
    }

    /** Empties the table; the import path clears before re-writing. */
    @Query("DELETE FROM relay_records")
    suspend fun deleteAll()
}

/**
 * Projection used when reconciling a device snapshot against the local table.
 */
data class RelayRecordIdEvent(
    val id: Long,
    @ColumnInfo(name = "event_id")
    val eventId: String?,
)
