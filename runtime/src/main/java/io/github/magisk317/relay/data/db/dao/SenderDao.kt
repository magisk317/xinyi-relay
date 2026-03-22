package io.github.magisk317.relay.data.db.dao

import androidx.room.*
import io.github.magisk317.relay.data.db.entity.SenderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderDao {

    @Insert
    fun insert(sender: SenderEntity): Long

    @Delete
    fun delete(sender: SenderEntity)

    @Query("DELETE FROM Sender where id=:id")
    fun delete(id: Long)

    @Query("DELETE FROM Sender")
    fun deleteAll()

    @Update
    fun update(sender: SenderEntity)

    @Query("UPDATE Sender SET status=:status WHERE id IN (:ids)")
    fun updateStatusByIds(ids: List<Long>, status: Int)

    @Query("SELECT * FROM Sender where id=:id")
    fun getOne(id: Long): SenderEntity?

    @Query("SELECT * FROM Sender WHERE id IN (:ids)")
    fun getByIds(ids: List<Long>): List<SenderEntity>

    @Query("SELECT * FROM Sender ORDER BY id DESC")
    fun getAll(): List<SenderEntity>

    @Query("SELECT * FROM Sender ORDER BY id DESC")
    fun getAllFlow(): Flow<List<SenderEntity>>
}
