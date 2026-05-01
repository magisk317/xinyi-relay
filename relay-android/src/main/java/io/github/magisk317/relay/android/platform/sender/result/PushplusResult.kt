package io.github.magisk317.relay.android.platform.sender.result

import androidx.annotation.Keep

@Keep
data class PushplusResult(
    var code: Long,
    var msg: String,
    var data: String?,
    var count: Long?,
)
