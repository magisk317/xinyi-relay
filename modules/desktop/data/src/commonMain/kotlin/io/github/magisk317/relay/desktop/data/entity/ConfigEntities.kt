package io.github.magisk317.relay.desktop.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_config_mirrors",
    indices = [],
)
data class DeviceConfigMirrorEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "device_id")
    val deviceId: Long,
    @ColumnInfo(name = "revision", defaultValue = "0")
    val revision: Long = 0,
    @ColumnInfo(name = "snapshot", defaultValue = "{}")
    val snapshot: String = "{}",
    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null,
)

@Entity(
    tableName = "device_config_commands",
    // Pending-target uniqueness is enforced by the PARTIAL index
    // `device_config_commands_pending_target_idx ... WHERE status = 'pending'`
    // installed from `PartialIndexes`, not by a full `@Index`. A full unique index
    // on (device_id, target_revision) would reject the folded duplicate the legacy
    // importer writes as a `failed` row sharing the kept row's target_revision.
    indices = [],
)
data class DeviceConfigCommandEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "device_id")
    val deviceId: Long = 0,
    @ColumnInfo(name = "base_revision")
    val baseRevision: Long = 0,
    @ColumnInfo(name = "target_revision")
    val targetRevision: Long = 0,
    @ColumnInfo(name = "mutation", defaultValue = "{}")
    val mutation: String = "{}",
    @ColumnInfo(name = "summary", defaultValue = "")
    val summary: String = "",
    @ColumnInfo(name = "actor_type", defaultValue = "desktop")
    val actorType: String = "desktop",
    @ColumnInfo(name = "actor_id", defaultValue = "0")
    val actorId: Long = 0,
    @ColumnInfo(name = "status", defaultValue = "pending")
    val status: String = "pending",
    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: String = "",
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = "",
    @ColumnInfo(name = "applied_at")
    val appliedAt: String? = null,
)

@Entity(
    tableName = "device_config_audit_logs",
    indices = [],
)
data class DeviceConfigAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "device_id")
    val deviceId: Long = 0,
    @ColumnInfo(name = "command_id")
    val commandId: Long? = null,
    @ColumnInfo(name = "revision", defaultValue = "0")
    val revision: Long = 0,
    @ColumnInfo(name = "event_type", defaultValue = "")
    val eventType: String = "",
    @ColumnInfo(name = "actor_type", defaultValue = "desktop")
    val actorType: String = "desktop",
    @ColumnInfo(name = "actor_id", defaultValue = "0")
    val actorId: Long = 0,
    @ColumnInfo(name = "summary", defaultValue = "")
    val summary: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: String = "",
)
