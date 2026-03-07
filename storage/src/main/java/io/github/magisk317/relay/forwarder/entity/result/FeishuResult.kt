package io.github.magisk317.relay.forwarder.entity.result

import androidx.annotation.Keep

@Keep
data class FeishuResult(
    var code: Long,
    var msg: String,
    var data: Any?,
)