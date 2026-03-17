package io.github.magisk317.relay.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Sender(
    var id: Long = 0,
    var type: Int = 1,
    var name: String = "",
    var jsonSetting: String = "",
    var status: Int = 1,
    var time: Date = Date(),
    var receiveCode: Int = 1,
    var receiveNonCode: Int = 0,
    var receiveAppNotify: Int = 1,
    var receiveCallNotify: Int = 0,
) : Parcelable
