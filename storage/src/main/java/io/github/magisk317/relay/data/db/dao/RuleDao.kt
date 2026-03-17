package io.github.magisk317.relay.data.db.dao

import androidx.room.*
import io.github.magisk317.relay.data.db.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Insert
    fun insert(rule: RuleEntity): Long

    @Delete
    fun delete(rule: RuleEntity)

    @Query("DELETE FROM Rule where id=:id")
    fun delete(id: Long)

    @Query("DELETE FROM Rule")
    fun deleteAll()

    @Update
    fun update(rule: RuleEntity)

    @Query("UPDATE Rule SET status=:status WHERE id IN (:ids)")
    fun updateStatusByIds(ids: List<Long>, status: Int)

    @Query("SELECT * FROM Rule where id=:id")
    fun getOne(id: Long): RuleEntity

    @Transaction
    @Query("SELECT * FROM Rule where type=:type and status=:status and (sim_slot='ALL' or sim_slot=:simSlot)")
    fun getRuleList(type: String, status: Int, simSlot: String): List<RuleEntity>

    @Query("SELECT * FROM Rule ORDER BY id DESC")
    fun getAll(): List<RuleEntity>

    @Query("SELECT * FROM Rule ORDER BY id DESC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM Rule WHERE sender_id=:senderId ORDER BY id DESC")
    fun observeBySender(senderId: Long): Flow<List<RuleEntity>>
}
