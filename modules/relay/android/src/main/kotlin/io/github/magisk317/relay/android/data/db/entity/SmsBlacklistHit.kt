package io.github.magisk317.relay.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.magisk317.relay.engine.model.ReadSmsBlacklistHitData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    tableName = "sms_blacklist_hit",
    indices = [
        Index(value = ["created_at"], name = "index_sms_blacklist_hit_created_at"),
        Index(value = ["event_id", "source"], unique = true, name = "index_sms_blacklist_hit_event_source"),
    ],
)
@Serializable
data class SmsBlacklistHit(
    @PrimaryKey(autoGenerate = true)
    @SerialName("id")
    override val id: Long = 0,

    @ColumnInfo(name = "event_id")
    @SerialName("eventId")
    override val eventId: String,

    @ColumnInfo(name = "source")
    @SerialName("source")
    override val source: String,

    @ColumnInfo(name = "sender")
    @SerialName("sender")
    override val sender: String? = null,

    @ColumnInfo(name = "body")
    @SerialName("body")
    override val body: String? = null,

    @ColumnInfo(name = "sms_date", defaultValue = "0")
    @SerialName("smsDate")
    override val smsDate: Long = 0L,

    @ColumnInfo(name = "match_type")
    @SerialName("matchType")
    override val matchType: String? = null,

    @ColumnInfo(name = "pattern")
    @SerialName("pattern")
    override val pattern: String? = null,

    @ColumnInfo(name = "action_delete", defaultValue = "0")
    @SerialName("actionDelete")
    override val actionDelete: Boolean = false,

    @ColumnInfo(name = "action_block", defaultValue = "0")
    @SerialName("actionBlock")
    override val actionBlock: Boolean = false,

    @ColumnInfo(name = "block_reason")
    @SerialName("blockReason")
    override val blockReason: String? = null,

    @ColumnInfo(name = "created_at", defaultValue = "0")
    @SerialName("createdAt")
    override val createdAt: Long = System.currentTimeMillis(),
) : ReadSmsBlacklistHitData
