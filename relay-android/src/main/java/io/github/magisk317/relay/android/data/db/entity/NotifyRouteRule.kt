package io.github.magisk317.relay.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.magisk317.relay.engine.model.NotifyRouteRuleData

@Entity(
    tableName = "notify_route_rule",
    indices = [
        Index(
            value = ["scope", "package_name", "sender_id"],
            unique = true,
            name = "index_notify_route_rule_scope_package_sender",
        ),
        Index(
            value = ["package_name", "scope"],
            name = "index_notify_route_rule_package_scope",
        ),
        Index(
            value = ["sender_id", "scope"],
            name = "index_notify_route_rule_sender_scope",
        ),
    ],
)
data class NotifyRouteRule(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    override val id: Long = 0L,
    @ColumnInfo(name = "scope")
    override val scope: Int,
    @ColumnInfo(name = "package_name")
    override val packageName: String,
    @ColumnInfo(name = "sender_id")
    override val senderId: Long,
    @ColumnInfo(name = "update_time", defaultValue = "0")
    override val updateTime: Long = 0L,
) : NotifyRouteRuleData
