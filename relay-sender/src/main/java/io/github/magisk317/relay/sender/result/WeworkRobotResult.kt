package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep

@Keep
data class WeworkRobotResult(
    var errcode: Long,
    var errmsg: String,
)
