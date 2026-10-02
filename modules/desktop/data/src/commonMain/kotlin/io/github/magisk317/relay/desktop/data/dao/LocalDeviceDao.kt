package io.github.magisk317.relay.desktop.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceBindCodeEntity
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceTokenEntity

@Dao
interface LocalDeviceDao {

    // ------------------------------------------------------------ bind codes

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBindCode(bindCode: LocalDeviceBindCodeEntity)

    @Query(
        """
        UPDATE local_device_bind_codes
           SET used_at = :now
         WHERE code_hash = :codeHash AND used_at IS NULL AND expires_at > :now
        """,
    )
    suspend fun consumeBindCode(codeHash: String, now: String): Int

    @Query("DELETE FROM local_device_bind_codes WHERE expires_at <= :now AND used_at IS NULL")
    suspend fun purgeExpiredBindCodes(now: String): Int

    // ------------------------------------------------------ device identity

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertToken(token: LocalDeviceTokenEntity)

    @Query(
        """
        SELECT d.id
          FROM local_device_tokens t
          JOIN devices d ON d.id = t.device_id
         WHERE t.token_hash = :tokenHash
           AND t.revoked_at IS NULL
           AND d.revoked_at IS NULL
           AND d.enabled = 1
        """,
    )
    suspend fun authenticate(tokenHash: String): Long?

    @Query("SELECT COUNT(*) FROM local_device_tokens WHERE token_hash = :tokenHash AND revoked_at IS NULL")
    suspend fun countActiveTokens(tokenHash: String): Int

    @Query("SELECT COUNT(*) FROM local_device_bind_codes")
    suspend fun countBindCodes(): Int

    @Query("SELECT COUNT(*) FROM local_device_tokens")
    suspend fun countTokens(): Int

    @Query(
        """
        UPDATE local_device_tokens
           SET revoked_at = :now
         WHERE device_id = :deviceId AND revoked_at IS NULL
        """,
    )
    suspend fun revokeToken(deviceId: Long, now: String): Int
}
