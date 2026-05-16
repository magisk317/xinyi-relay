package io.github.magisk317.relay.engine.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object RelayHttpClients {
    private const val DEFAULT_TIMEOUT_SECONDS = 15L

    val default: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun newBuilder(): OkHttpClient.Builder {
        return default.newBuilder()
    }
}
