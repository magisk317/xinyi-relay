package com.github.magisk317.smscode.forwarder.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.github.magisk317.smscode.forwarder.database.ext.ConvertersDate
import com.github.magisk317.smscode.forwarder.database.ext.ConvertersSenderList
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
@Entity(
    tableName = "Rule",
    foreignKeys = [
        ForeignKey(
            entity = Sender::class,
            parentColumns = ["id"],
            childColumns = ["sender_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["sender_id"]),
        Index(value = ["sender_list"])
    ]
)
@TypeConverters(ConvertersDate::class, ConvertersSenderList::class)
data class Rule(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "type", defaultValue = "sms") var type: String,
    @ColumnInfo(name = "filed", defaultValue = "transpond_all") var filed: String,
    @ColumnInfo(name = "check", defaultValue = "is") var check: String,
    @ColumnInfo(name = "value", defaultValue = "") var value: String,
    @ColumnInfo(name = "sender_id", defaultValue = "0") var senderId: Long = 0,
    @ColumnInfo(name = "sms_template", defaultValue = "") var smsTemplate: String = "",
    @ColumnInfo(name = "regex_replace", defaultValue = "") var regexReplace: String = "",
    @ColumnInfo(name = "sim_slot", defaultValue = "ALL") var simSlot: String = "",
    @ColumnInfo(name = "status", defaultValue = "1") var status: Int = 1,
    @ColumnInfo(name = "time") var time: Date = Date(),
    @ColumnInfo(name = "sender_list", defaultValue = "") var senderList: List<Sender>,
    @ColumnInfo(name = "sender_logic", defaultValue = "ALL") var senderLogic: String = "ALL",
    //免打扰(禁用转发)时间段
    @ColumnInfo(name = "silent_period_start", defaultValue = "0") var silentPeriodStart: Int = 0,
    @ColumnInfo(name = "silent_period_end", defaultValue = "0") var silentPeriodEnd: Int = 0,
    @ColumnInfo(name = "silent_day_of_week", defaultValue = "") var silentDayOfWeek: String = "",
    @ColumnInfo(name = "title", defaultValue = "") var title: String = "",
) : Parcelable {

    // Complex business logic like getRuleMatch, statusImageId, checkMsg etc. 
    // have been removed to decouple from UI and old SmsForwarder utility classes.
    // Rule is now purely a data container for Room entity processing.
}