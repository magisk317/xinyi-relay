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
    val silentPeriod: ForwardSilentPeriodConfig = ForwardSilentPeriodConfig(),
)

@Serializable
data class ForwardSilentPeriodConfig(
    val enabled: Boolean = false,
    val start: String = DEFAULT_START,
    val end: String = DEFAULT_END,
    val weekdays: List<Int> = ALL_WEEKDAYS,
) {
    companion object {
        const val DEFAULT_START = "22:00"
        const val DEFAULT_END = "08:00"
        val ALL_WEEKDAYS: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7)
    }
}
