package com.github.magisk317.smscode.forwarder.entity.result

import androidx.annotation.Keep

@Keep
data class PushplusResult(
    var code: Long,
    var msg: String,
    var data: String?,
    var count: Long?,
)