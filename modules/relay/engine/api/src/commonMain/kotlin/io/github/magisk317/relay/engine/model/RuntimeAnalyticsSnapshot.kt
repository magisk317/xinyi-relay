package io.github.magisk317.relay.engine.model

data class RuntimeAnalyticsSnapshot(
    val totalMessages: Long,
    val codeDetected: Long,
    val autoInputAttempt: Long,
    val autoInputSuccess: Long,
    val autoInputFailed: Long,
    val forwardTotal: Long,
    val forwardSuccess: Long,
    val forwardFailed: Long,
    val senderStats: List<SenderDispatchStat>,
)

data class SenderConfigurationSnapshot(
    val configuredByType: Map<Int, Int>,
    val enabledByType: Map<Int, Int>,
)
