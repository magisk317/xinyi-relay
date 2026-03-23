package io.github.magisk317.relay.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Rule(
    var id: Long = 0,
    var type: String = "sms",
    var filed: String = "transpond_all",
    var check: String = "is",
    var value: String = "",
    var senderId: Long = 0,
    var smsTemplate: String = "",
    var regexReplace: String = "",
    var simSlot: String = "",
    var status: Int = 1,
    var time: Date = Date(),
    var senderList: List<Sender> = emptyList(),
    var senderLogic: String = "ALL",
    var silentPeriodStart: Int = 0,
    var silentPeriodEnd: Int = 0,
    var silentDayOfWeek: String = "",
    var title: String = "",
) : Parcelable
