package com.github.magisk317.smscode.forwarder.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.TypeConverters
import com.github.magisk317.smscode.forwarder.database.ext.ConvertersDate
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
@Entity(tableName = "Sender")
@TypeConverters(ConvertersDate::class)
data class Sender(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "type", defaultValue = "1") var type: Int = 1,
    @ColumnInfo(name = "name", defaultValue = "") var name: String,
    @ColumnInfo(name = "json_setting", defaultValue = "") var jsonSetting: String,
    @ColumnInfo(name = "status", defaultValue = "1") var status: Int = 1,
    @ColumnInfo(name = "time") var time: Date = Date(),
    /** 0 = 不转发验证码短信，1 = 转发验证码短信 */
    @ColumnInfo(name = "receive_code", defaultValue = "1") var receiveCode: Int = 1,
    /** 0 = 仅验证码短信，1 = 所有短信（含非验证码）均转发 */
    @ColumnInfo(name = "receive_non_code", defaultValue = "0") var receiveNonCode: Int = 0,
    /** 0 = 不转发应用通知，1 = 转发应用通知 */
    @ColumnInfo(name = "receive_app_notify", defaultValue = "1") var receiveAppNotify: Int = 1,
    /** 0 = 不转发通话通知，1 = 转发通话通知 */
    @ColumnInfo(name = "receive_call_notify", defaultValue = "0") var receiveCallNotify: Int = 0,
) : Parcelable
