package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.engine.model.RuntimeAnalyticsSnapshot
import io.github.magisk317.relay.engine.model.SenderConfigurationSnapshot

interface RuntimeAnalyticsProvider {
    suspend fun snapshot(fromMs: Long): RuntimeAnalyticsSnapshot
    suspend fun senderConfigurationSnapshot(): SenderConfigurationSnapshot
}
