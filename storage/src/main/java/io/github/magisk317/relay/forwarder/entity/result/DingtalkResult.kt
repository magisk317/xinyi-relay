package io.github.magisk317.relay.forwarder.entity.result

import androidx.annotation.Keep

@Keep
data class DingtalkResult(
    var errcode: Long,
    var errmsg: String,
)