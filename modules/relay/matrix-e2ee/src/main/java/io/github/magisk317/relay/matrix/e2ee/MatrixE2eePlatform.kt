package io.github.magisk317.relay.matrix.e2ee

import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.TracingFileConfiguration
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
                    writeToStdoutOrSystem = false,
                    writeToFiles = TracingFileConfiguration(
                        path = "/dev/null",
                        filePrefix = "",
                        fileSuffix = "",
                        maxTotalSizeBytes = 0u,
                        maxAgeSeconds = 0u,
                    ),
                    sentryConfig = null,
                ),
                useLightweightTokioRuntime = false,
            )
            initialized = true
        }
    }
}
