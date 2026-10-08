package io.github.magisk317.relay.android.data.db.dao

import androidx.room.*
import io.github.magisk317.smscode.db.entity.Sender
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderDao {

    @Insert
    suspend fun insert(sender: Sender): Long

    @Delete
    suspend fun delete(sender: Sender)

    @Query("DELETE FROM Sender where id=:id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Sender")
    suspend fun deleteAll()

    @Update
    suspend fun update(sender: Sender)

    @Query("UPDATE Sender SET status=:status WHERE id IN (:ids)")
    suspend fun updateStatusByIds(ids: List<Long>, status: Int)

    @Query("UPDATE Sender SET priority=:priority WHERE id=:id")
    suspend fun updatePriorityById(id: Long, priority: Int)

    @Query("SELECT * FROM Sender where id=:id")
    suspend fun getOne(id: Long): Sender?

    @Query("SELECT * FROM Sender WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Sender>

    @Query("SELECT * FROM Sender ORDER BY priority ASC, id DESC")
    suspend fun getAll(): List<Sender>

    @Query("SELECT * FROM Sender ORDER BY priority ASC, id DESC")
    fun getAllFlow(): Flow<List<Sender>>
}
