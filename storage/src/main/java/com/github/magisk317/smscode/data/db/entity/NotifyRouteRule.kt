package com.github.magisk317.smscode.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val id: Long = 0L,
    @ColumnInfo(name = "scope")
    val scope: Int,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "sender_id")
    val senderId: Long,
    @ColumnInfo(name = "update_time", defaultValue = "0")
    val updateTime: Long = 0L,
)
