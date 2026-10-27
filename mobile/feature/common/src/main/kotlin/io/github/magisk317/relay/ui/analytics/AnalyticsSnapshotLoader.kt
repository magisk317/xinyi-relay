package io.github.magisk317.relay.ui.analytics

import io.github.magisk317.relay.engine.service.RuntimeAnalyticsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun loadAnalyticsSnapshot(
    provider: RuntimeAnalyticsProvider,
    chartWindow: AnalyticsChartWindow,
    analyticsEnabled: Boolean,
): AnalyticsSnapshot? = withContext(Dispatchers.IO) {
    if (!analyticsEnabled) {
        null
    } else {
        val snapshot = provider.snapshot(chartWindow.fromMs(System.currentTimeMillis()))
        AnalyticsSnapshot(
            totalMessages = snapshot.totalMessages,
            codeDetected = snapshot.codeDetected,
            autoInputAttempt = snapshot.autoInputAttempt,
            autoInputSuccess = snapshot.autoInputSuccess,
            autoInputFailed = snapshot.autoInputFailed,
            forwardTotal = snapshot.forwardTotal,
            forwardSuccess = snapshot.forwardSuccess,
            forwardFailed = snapshot.forwardFailed,
            senderStats = snapshot.senderStats,
        )
    }
}
