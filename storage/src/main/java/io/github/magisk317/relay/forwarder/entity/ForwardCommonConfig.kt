package io.github.magisk317.relay.forwarder.entity

data class ForwardCommonConfig(
    val deviceName: String = "",
    val messageTemplate: String = "",
    val includeTime: Boolean = false,
    val includeSender: Boolean = false,
    val includeDeviceName: Boolean = true,
)
