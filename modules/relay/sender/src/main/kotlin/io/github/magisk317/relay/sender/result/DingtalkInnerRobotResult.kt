package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable

@Keep
@KotlinSerializable
data class DingtalkInnerRobotResult(
    //获取access_token返回
    var accessToken: String? = null,
    var expireIn: Long? = null,
    //消息id
    var processQueryKey: String? = null,
    //无效的用户userid列表
    //var invalidStaffIdList: String[],
    //被限流的userid列表
    //var flowControlledStaffIdList: String[],
)
