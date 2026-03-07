package com.github.magisk317.smscode.forwarder.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "forward_filter_rule",
    indices = [
        Index(
            value = ["msg_type", "scope_type", "scope_key", "sender_id", "policy", "match_mode", "pattern"],
            unique = true,
            name = "index_forward_filter_rule_unique",
        ),
        Index(
            value = ["msg_type", "scope_type", "scope_key"],
            name = "index_forward_filter_rule_msg_scope_key",
        ),
        Index(
            value = ["msg_type", "scope_type", "sender_id"],
            name = "index_forward_filter_rule_msg_scope_sender",
        ),
    ],
)
data class ForwardFilterRule(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "msg_type")
    val msgType: String,
    @ColumnInfo(name = "scope_type")
    val scopeType: String,
    @ColumnInfo(name = "scope_key")
    val scopeKey: String = "",
    @ColumnInfo(name = "sender_id", defaultValue = "0")
    val senderId: Long = 0L,
    @ColumnInfo(name = "policy")
    val policy: String,
    @ColumnInfo(name = "match_mode")
    val matchMode: String,
    @ColumnInfo(name = "pattern")
    val pattern: String,
    @ColumnInfo(name = "enabled", defaultValue = "1")
    val enabled: Int = 1,
    @ColumnInfo(name = "update_time", defaultValue = "0")
    val updateTime: Long = 0L,
)
