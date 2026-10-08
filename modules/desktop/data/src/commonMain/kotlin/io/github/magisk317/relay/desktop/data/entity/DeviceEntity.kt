package io.github.magisk317.relay.desktop.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Local mirror of the devices table owned by the desktop client.
 *
 * The column set is intentionally byte-for-byte compatible with the legacy
 * Rust schema (the retired Tauri shell's sqlite_store.rs) so a legacy
 * database can be imported without rewriting rows.
 */
@Serializable
@Entity(
    tableName = "devices",
    indices = [],
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long = 0,
    @ColumnInfo(name = "device_name", defaultValue = "")
    val deviceName: String = "",
    @ColumnInfo(name = "device_model", defaultValue = "")
    val deviceModel: String = "",
    @ColumnInfo(name = "platform", defaultValue = "android")
    val platform: String = "android",
    @ColumnInfo(name = "app_version", defaultValue = "")
    val appVersion: String = "",
    @ColumnInfo(name = "display_name", defaultValue = "")
    val displayName: String = "",
    @ColumnInfo(name = "enabled", defaultValue = "1")
    val enabled: Boolean = true,
    @ColumnInfo(name = "revoked_at")
    val revokedAt: String? = null,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: String? = null,
    @ColumnInfo(name = "local_addresses", defaultValue = "[]")
    val localAddresses: String = "[]",
    @ColumnInfo(name = "capabilities", defaultValue = "{}")
    val capabilities: String = "{}",
    @ColumnInfo(name = "created_at")
    val createdAt: String = "",
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = "",
)
