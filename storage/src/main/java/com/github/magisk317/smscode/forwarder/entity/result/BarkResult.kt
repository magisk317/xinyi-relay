package com.github.magisk317.smscode.forwarder.entity.result

import androidx.annotation.Keep

@Keep
data class BarkResult(
    var code: Long,
    var message: String,
    var timestamp: Long?,
)