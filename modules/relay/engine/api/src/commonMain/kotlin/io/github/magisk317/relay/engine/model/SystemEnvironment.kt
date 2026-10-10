package io.github.magisk317.relay.engine.model

import io.github.magisk317.relay.contract.model.PlatformSerializable

data class BatterySnapshot(
    val percent: String = "",
    val status: String = "",
    val plugged: String = "",
    val fullInfo: String = "",
    val simpleInfo: String = "",
) : PlatformSerializable

data class NetworkSnapshot(
    val netType: String = "",
    val ipv4: String = "",
    val ipv6: String = "",
    val ipList: String = "",
) : PlatformSerializable

data class SystemEnvironment(
    val deviceName: String,
    val appVersion: String,
    val battery: BatterySnapshot,
    val network: NetworkSnapshot,
    val currentTime: Long,
) : PlatformSerializable
