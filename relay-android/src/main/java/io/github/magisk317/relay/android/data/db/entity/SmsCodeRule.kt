package io.github.magisk317.relay.android.data.db.entity

import android.os.Parcelable
import androidx.room.*
import io.github.magisk317.smscode.runtime.common.backup.BackupConst
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import io.github.magisk317.relay.engine.model.SmsCodeRuleData
import kotlinx.serialization.Serializable

@Entity(
    tableName = "sms_code_rule",
    indices = [
        Index(value = ["company", "code_keyword", "code_regex"], unique = true),
    ],
)
@Serializable
@Parcelize
data class SmsCodeRule @JvmOverloads constructor(
    @ColumnInfo(name = "company")
    @SerialName(BackupConst.KEY_COMPANY)
    override val company: String? = null,

    @ColumnInfo(name = "code_keyword")
    @SerialName(BackupConst.KEY_CODE_KEYWORD)
    override val codeKeyword: String = "",

    @ColumnInfo(name = "code_regex")
    @SerialName(BackupConst.KEY_CODE_REGEX)
    override val codeRegex: String = "",

    @PrimaryKey(autoGenerate = true)
    override val id: Long = 0,
) : Parcelable, SmsCodeRuleData
