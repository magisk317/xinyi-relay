package io.github.magisk317.relay.matrix.e2ee

import android.content.Context
import android.content.ContextWrapper
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform
import org.matrix.rustsdk.Android

@Suppress("TooGenericExceptionCaught")
object MatrixE2eePlatform {
    private val lock = Any()

    @Volatile
    private var initialized = false

    @Volatile
    private var initializationFailure: Throwable? = null

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            initializationFailure?.let { failure ->
                throw IllegalStateException(
                    "Matrix E2EE platform initialization previously failed: ${failure.message}",
                    failure,
                )
            }
            try {
                Android.init(PluginClassLoaderContext(context.applicationContext ?: context))
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
            } catch (failure: Throwable) {
                initializationFailure = failure
                throw IllegalStateException(
                    "Matrix E2EE platform initialization failed: " +
                        "${failure.javaClass.simpleName}: ${failure.message}",
                    failure,
                )
            }
        }
    }

    fun isInitialized(): Boolean = initialized

    fun initializationFailure(): Throwable? = initializationFailure

    /**
     * rustls-platform-verifier keeps this Context and resolves
     * org.rustls.platformverifier.CertificateVerifier through its classloader.
     * In the plugin APK that class only exists in the plugin's DexClassLoader,
     * not in the host application classloader.
     */
    private class PluginClassLoaderContext(base: Context) : ContextWrapper(base) {
        private val pluginClassLoader: ClassLoader = MatrixE2eePlatform::class.java.classLoader

        override fun getClassLoader(): ClassLoader = pluginClassLoader
    }
}
