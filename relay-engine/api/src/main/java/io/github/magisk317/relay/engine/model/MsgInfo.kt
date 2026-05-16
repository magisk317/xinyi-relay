package io.github.magisk317.relay.engine.model

import java.io.Serializable
import java.util.Date

@Suppress("unused")
data class MsgInfo(
    val type: String = "sms",
    val from: String,
    val content: String,
    val date: Date,
    val simInfo: String,
    val simSlot: Int = -1,
    val subId: Int = 0,
    val callType: Int = 0,
    val uid: Int = 0,
    val packageName: String = "",
    val notifyChannelId: String = "",
    val appName: String = "",
    val title: String = "",
    val message: String = "",
    val contactName: String = "",
    val phoneArea: String = "",
) : Serializable
