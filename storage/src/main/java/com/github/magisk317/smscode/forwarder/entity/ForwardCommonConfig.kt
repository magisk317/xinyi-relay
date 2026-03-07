package com.github.magisk317.smscode.forwarder.entity

data class ForwardCommonConfig(
    val deviceName: String = "",
    val messageTemplate: String = "",
    val includeTime: Boolean = false,
    val includeSender: Boolean = false,
    val includeDeviceName: Boolean = true,
)
