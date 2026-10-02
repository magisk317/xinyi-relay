package io.github.magisk317.relay.desktop.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The partial unique index below is the idempotency anchor for record sync.
 *
 * The legacy Rust schema declares it as:
 *
 * ```sql
 * CREATE UNIQUE INDEX relay_records_device_event_id_idx
 *     ON relay_records(device_id, event_id) WHERE event_id IS NOT NULL;
 * ```
 *
 * Room has no annotation for partial indexes, so the table is declared without
 * it and [io.github.magisk317.relay.desktop.data.DesktopDatabase] installs the
 * partial index from its open callback. Rows with a NULL `event_id` are
 * therefore allowed to repeat, exactly as in the legacy store.
 */
@Entity(
    tableName = "relay_records",
    indices = [],
)
data class RelayRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "user_id", defaultValue = "0")
    val userId: Long = 0,
    @ColumnInfo(name = "device_id", defaultValue = "0")
    val deviceId: Long = 0,
    @ColumnInfo(name = "event_id")
    val eventId: String? = null,
    @ColumnInfo(name = "record_type", defaultValue = "")
    val recordType: String = "",
    @ColumnInfo(name = "sender", defaultValue = "")
    val sender: String = "",
    @ColumnInfo(name = "body", defaultValue = "")
    val body: String = "",
    @ColumnInfo(name = "sms_code", defaultValue = "")
    val smsCode: String = "",
    @ColumnInfo(name = "package_name", defaultValue = "")
    val packageName: String = "",
    @ColumnInfo(name = "msg_type", defaultValue = "0")
    val msgType: Long = 0,
    @ColumnInfo(name = "call_type", defaultValue = "0")
    val callType: Long = 0,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: String = "",
    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: String = "",
    @ColumnInfo(name = "metadata", defaultValue = "{}")
    val metadata: String = "{}",
)

/**
 * Bind codes are one-shot: the code itself is never persisted, only its
 * SHA-256 digest. `usedAt` non-null means the code has been consumed.
 */
@Entity(
    tableName = "local_device_bind_codes",
    indices = [],
)
data class LocalDeviceBindCodeEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "code_hash")
    val codeHash: String,
    @ColumnInfo(name = "expires_at")
    val expiresAt: String,
    @ColumnInfo(name = "used_at")
    val usedAt: String? = null,
)

@Entity(
    tableName = "local_device_tokens",
    indices = [],
)
data class LocalDeviceTokenEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "device_id")
    val deviceId: Long,
    @ColumnInfo(name = "token_hash")
    val tokenHash: String,
    @ColumnInfo(name = "revoked_at")
    val revokedAt: String? = null,
)
