package io.github.magisk317.relay.android.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.magisk317.relay.android.data.db.entity.ScheduledTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_task ORDER BY id DESC")
    fun getAllFlow(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_task")
    suspend fun getAll(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_task WHERE status = 1")
    suspend fun getActiveTasks(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_task WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduledTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ScheduledTaskEntity): Long

    @Update
    suspend fun update(task: ScheduledTaskEntity)

    @Delete
    suspend fun delete(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_task WHERE id = :id")
    suspend fun deleteById(id: Long)
}
