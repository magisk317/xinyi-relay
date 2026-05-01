package io.github.magisk317.relay.android.data.db.dao

import androidx.room.*
import io.github.magisk317.relay.android.data.db.entity.SenderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderDao {

    @Insert
    suspend fun insert(sender: SenderEntity): Long

    @Delete
    suspend fun delete(sender: SenderEntity)

    @Query("DELETE FROM Sender where id=:id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Sender")
    suspend fun deleteAll()

    @Update
    suspend fun update(sender: SenderEntity)

    @Query("UPDATE Sender SET status=:status WHERE id IN (:ids)")
    suspend fun updateStatusByIds(ids: List<Long>, status: Int)

    @Query("SELECT * FROM Sender where id=:id")
    suspend fun getOne(id: Long): SenderEntity?

    @Query("SELECT * FROM Sender WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SenderEntity>

    @Query("SELECT * FROM Sender ORDER BY id DESC")
    suspend fun getAll(): List<SenderEntity>

    @Query("SELECT * FROM Sender ORDER BY id DESC")
    fun getAllFlow(): Flow<List<SenderEntity>>
}
