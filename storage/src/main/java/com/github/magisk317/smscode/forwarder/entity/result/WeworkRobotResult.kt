package com.github.magisk317.smscode.forwarder.entity.result

import androidx.annotation.Keep

@Keep
data class WeworkRobotResult(
    var errcode: Long,
    var errmsg: String,
)