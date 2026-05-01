package io.github.magisk317.relay.android.platform.sender.result

import androidx.annotation.Keep

@Keep
@Suppress("PropertyName")
data class WeworkAgentResult(
    var errcode: Long,
    var errmsg: String,
    //获取access_token返回
    var access_token: String?,
    var expires_in: Long?,
    //发送接口返回
    var msgid: String?,
)
