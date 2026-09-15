package io.github.magisk317.relay.matrix.e2ee

import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform

object MatrixE2eePlatform {
    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initPlatform(
                TracingConfiguration(
                    logLevel = LogLevel.INFO,
                    traceLogPacks = emptyList(),
                    extraTargets = emptyList(),
                    writeToStdoutOrSystem = true,
                    writeToFiles = null,
                    sentryConfig = null,
                ),
                useLightweightTokioRuntime = false,
            )
            initialized = true
        }
    }
}
