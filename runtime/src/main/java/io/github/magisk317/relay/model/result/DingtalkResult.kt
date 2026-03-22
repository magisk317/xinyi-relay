package io.github.magisk317.relay.model.result

import androidx.annotation.Keep

@Keep
data class DingtalkResult(
    var errcode: Long,
    var errmsg: String,
)