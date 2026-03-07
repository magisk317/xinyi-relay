package com.github.magisk317.smscode.data.db.entity

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.room.*
import com.github.magisk317.smscode.feature.backup.BackupConst
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
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
    val company: String? = null,

    @ColumnInfo(name = "code_keyword")
    @SerialName(BackupConst.KEY_CODE_KEYWORD)
    val codeKeyword: String = "",

    @ColumnInfo(name = "code_regex")
    @SerialName(BackupConst.KEY_CODE_REGEX)
    val codeRegex: String = "",

    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
) : Parcelable
