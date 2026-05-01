package io.github.magisk317.relay.engine.model

data class BatterySnapshot(
    val percent: String = "",
    val status: String = "",
    val plugged: String = "",
    val fullInfo: String = "",
    val simpleInfo: String = "",
)

data class NetworkSnapshot(
    val netType: String = "",
    val ipv4: String = "",
    val ipv6: String = "",
    val ipList: String = "",
)

data class SystemEnvironment(
    val deviceName: String,
    val appVersion: String,
    val battery: BatterySnapshot,
    val network: NetworkSnapshot,
    val currentTime: Long,
)
