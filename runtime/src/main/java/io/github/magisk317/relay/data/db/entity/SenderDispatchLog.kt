package io.github.magisk317.relay.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sender_dispatch_log",
    indices = [
        Index(value = ["sender_type", "created_at"], name = "index_sender_dispatch_type_time"),
        Index(value = ["sender_id", "created_at"], name = "index_sender_dispatch_sender_time"),
        Index(value = ["record_id"], name = "index_sender_dispatch_record"),
    ],
)
data class SenderDispatchLog(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "record_id")
    val recordId: Long? = null,

    @ColumnInfo(name = "sender_id")
    val senderId: Long,

    @ColumnInfo(name = "sender_type")
    val senderType: Int,

    @ColumnInfo(name = "msg_type")
    val msgType: Int,

    @ColumnInfo(name = "success")
    val success: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
