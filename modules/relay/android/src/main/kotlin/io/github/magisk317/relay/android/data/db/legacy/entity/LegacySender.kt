package io.github.magisk317.relay.android.data.db.legacy.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/** Legacy-only sender row used by relay_room.db import and compatibility reads. */
@Parcelize
@Entity(tableName = "Sender")
@TypeConverters(LegacyConvertersDate::class)
data class LegacySender(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "type", defaultValue = "1") var type: Int = 1,
    @ColumnInfo(name = "name", defaultValue = "") var name: String,
    @ColumnInfo(name = "json_setting", defaultValue = "") var jsonSetting: String,
    @ColumnInfo(name = "status", defaultValue = "1") var status: Int = 1,
    @ColumnInfo(name = "time") var time: Date = Date(),
    @ColumnInfo(name = "receive_code", defaultValue = "1") var receiveCode: Int = 1,
    @ColumnInfo(name = "receive_non_code", defaultValue = "0") var receiveNonCode: Int = 0,
    @ColumnInfo(name = "receive_app_notify", defaultValue = "1") var receiveAppNotify: Int = 1,
    @ColumnInfo(name = "receive_call_notify", defaultValue = "0") var receiveCallNotify: Int = 0,
    @ColumnInfo(name = "active_schedule_json", defaultValue = "") var activeScheduleJson: String = "",
    @ColumnInfo(name = "priority", defaultValue = "0") var priority: Int = 0,
    @ColumnInfo(name = "custom_template", defaultValue = "") var customTemplate: String = "",
) : Parcelable
