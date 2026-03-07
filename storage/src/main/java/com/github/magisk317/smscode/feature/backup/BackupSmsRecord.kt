package com.github.magisk317.smscode.feature.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupSmsRecord(
    @SerialName("sender")
    val sender: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("date")
    val date: Long = 0,
    @SerialName("company")
    val company: String? = null,
    @SerialName("code")
    val smsCode: String? = null,
    @SerialName("packageName")
    val packageName: String? = null,

    @SerialName("msgType")
    val msgType: Int = 0,

    @SerialName("callType")
    val callType: Int = 0,

    @SerialName("forwardStatus")
    val forwardStatus: Int = 0,

    @SerialName("forwardTarget")
    val forwardTarget: String? = null,

    @SerialName("forwardMessage")
    val forwardMessage: String? = null,

    @SerialName("forwardTime")
    val forwardTime: Long = 0L,
)
