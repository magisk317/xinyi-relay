package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import io.github.magisk317.relay.contract.model.ProxyType


@KotlinSerializable
data class DingtalkInnerRobotSetting(
    val agentID: String = "",
    val appKey: String = "",
    val appSecret: String = "",
    val userIds: String = "",
    val msgKey: String = "sampleText",
    val titleTemplate: String = "",
    @KotlinSerializable(with = ProxyTypeSerializer::class)
    val proxyType: ProxyType = ProxyType.DIRECT,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyAuthenticator: Boolean = false,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
) {

}
