package io.github.magisk317.relay.net

import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

object RelayHttpClients {
    private const val DEFAULT_TIMEOUT_SECONDS = 15L
    private const val CALL_TIMEOUT_SECONDS = 30L

    val default: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SECONDS.seconds)
        .readTimeout(DEFAULT_TIMEOUT_SECONDS.seconds)
        .writeTimeout(DEFAULT_TIMEOUT_SECONDS.seconds)
        .callTimeout(CALL_TIMEOUT_SECONDS.seconds)
        .build()

    fun newBuilder(): OkHttpClient.Builder {
        return default.newBuilder()
    }
}
