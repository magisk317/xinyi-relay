package com.github.magisk317.smscode.data.db.entity

import android.content.Intent
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.magisk317.smscode.common.utils.SmsMessageUtils
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer

@Immutable
@Entity(
    tableName = "sms_msg",
    indices = [
        androidx.room.Index(value = ["sender", "body", "date", "msg_type"], unique = true),
        androidx.room.Index(
            value = ["package_name", "msg_type", "notify_channel_id", "date"],
            name = "index_sms_msg_pkg_type_channel_date",
        ),
    ],
)
@Parcelize
@Serializable
data class SmsMsg(
    @PrimaryKey(autoGenerate = true)
    @SerialName("id")
    val id: Long? = null,

    @ColumnInfo(name = "sender")
    @SerialName("sender")
    val sender: String? = null,

    @ColumnInfo(name = "body")
    @SerialName("body")
    val body: String? = null,

    @ColumnInfo(name = "date")
    @SerialName("date")
    val date: Long = 0,

    @ColumnInfo(name = "company")
    @SerialName("company")
    val company: String? = null,

    @ColumnInfo(name = "sms_code")
    @SerialName("code")
    val smsCode: String? = null,

    @ColumnInfo(name = "package_name")
    @SerialName("packageName")
    val packageName: String? = null,

    @ColumnInfo(name = "notify_channel_id", defaultValue = "''")
    @SerialName("notifyChannelId")
    val notifyChannelId: String = "",

    @ColumnInfo(name = "forward_status")
    @SerialName("forwardStatus")
    var forwardStatus: Int = FORWARD_STATUS_NONE,

    @ColumnInfo(name = "forward_target")
    @SerialName("forwardTarget")
    var forwardTarget: String? = null,

    @ColumnInfo(name = "forward_message")
    @SerialName("forwardMessage")
    var forwardMessage: String? = null,

    @ColumnInfo(name = "forward_time")
    @SerialName("forwardTime")
    var forwardTime: Long = 0L,

    @ColumnInfo(name = "msg_type", defaultValue = "0")
    @SerialName("msgType")
    val msgType: Int = MSG_TYPE_SMS,

    @ColumnInfo(name = "call_type", defaultValue = "0")
    @SerialName("callType")
    val callType: Int = 0,

) : Parcelable {

    companion object {
        const val FORWARD_STATUS_NONE = 0
        const val FORWARD_STATUS_SUCCESS = 1
        const val FORWARD_STATUS_FAILED = 2
        const val FORWARD_STATUS_PARTIAL = 3
        const val FORWARD_STATUS_BLOCKED = 4

        const val MSG_TYPE_SMS = 0
        const val MSG_TYPE_APP_NOTIFY = 1
        const val MSG_TYPE_CALL_NOTIFY = 2

        @JvmStatic
        fun fromIntent(intent: Intent): SmsMsg {
            val smsMessageParts = SmsMessageUtils.fromIntent(intent)
            if (smsMessageParts.isEmpty()) return SmsMsg()

            var sender = smsMessageParts[0].displayOriginatingAddress
            var body = SmsMessageUtils.getMessageBody(smsMessageParts)
            val date = smsMessageParts[0].timestampMillis

            sender = Normalizer.normalize(sender, Normalizer.Form.NFC)
            body = Normalizer.normalize(body, Normalizer.Form.NFC)

            return SmsMsg(
                sender = sender,
                body = body,
                date = date,
                msgType = MSG_TYPE_SMS,
            )
        }
    }
}
