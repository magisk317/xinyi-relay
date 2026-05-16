package io.github.magisk317.relay.contract.model

import io.github.magisk317.relay.contract.constant.DispatchStrategy
import kotlinx.serialization.Serializable

@Serializable
data class ForwardCommonConfig(
    val deviceName: String = "",
    val messageTemplate: String = "",
    val includeTime: Boolean = false,
    val includeSender: Boolean = false,
    val includeDeviceName: Boolean = true,
    val dispatchStrategy: Int = DispatchStrategy.BROADCAST_ALL,
)
