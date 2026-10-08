package io.github.magisk317.relay.android.data.db.dao

import androidx.room.*
import io.github.magisk317.smscode.db.entity.Rule
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Insert
    suspend fun insert(rule: Rule): Long

    @Delete
    suspend fun delete(rule: Rule)

    @Query("DELETE FROM Rule where id=:id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Rule")
    suspend fun deleteAll()

    @Update
    suspend fun update(rule: Rule)

    @Query("UPDATE Rule SET status=:status WHERE id IN (:ids)")
    suspend fun updateStatusByIds(ids: List<Long>, status: Int)

    @Query("SELECT * FROM Rule where id=:id")
    suspend fun getOne(id: Long): Rule

    @Transaction
    @Query("SELECT * FROM Rule where type=:type and status=:status and (sim_slot='ALL' or sim_slot=:simSlot)")
    suspend fun getRuleList(type: String, status: Int, simSlot: String): List<Rule>

    @Query("SELECT * FROM Rule ORDER BY id DESC")
    suspend fun getAll(): List<Rule>

    @Query("SELECT * FROM Rule ORDER BY id DESC")
    fun observeAll(): Flow<List<Rule>>

    @Query("SELECT * FROM Rule WHERE sender_id=:senderId ORDER BY id DESC")
    fun observeBySender(senderId: Long): Flow<List<Rule>>
}
