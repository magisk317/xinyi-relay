package io.github.magisk317.relay.desktop.remote

import io.github.magisk317.relay.net.RelayHttpClients
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Per-profile TLS override for a backend whose console certificate is
 * self-signed (the profile's `allowSelfSigned` flag).
 *
 * The default path is never widened: with the flag off the callers keep the
 * shared [RelayHttpClients.default] client, system trust store and hostname
 * verification included. Only a profile that opts in gets a client that
 * accepts its certificate chain, so the reachability trade stays a deliberate
 * per-profile choice instead of a process-wide downgrade.
 */
object SelfSignedTls {

    /**
     * The client for [allowSelfSigned]: an accepting one when the profile set
     * the flag, otherwise [fallback] (the caller's normal client).
     */
    fun client(
        allowSelfSigned: Boolean,
        fallback: OkHttpClient = RelayHttpClients.default,
    ): OkHttpClient = if (allowSelfSigned) newBuilder(allowSelfSigned = true).build() else fallback

    /**
     * The shared client builder, extended with an all-accepting trust manager
     * and hostname verifier when [allowSelfSigned] is on. Callers that need
     * their own timeouts (the realtime socket) build from this.
     */
    fun newBuilder(allowSelfSigned: Boolean): OkHttpClient.Builder {
        val builder = RelayHttpClients.newBuilder()
        if (!allowSelfSigned) return builder
        return builder
            .sslSocketFactory(insecureContext().socketFactory, TRUST_MANAGER)
            .hostnameVerifier { _, _ -> true }
    }

    private fun insecureContext(): SSLContext =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(TRUST_MANAGER), SecureRandom()) }

    /**
     * Accepts every chain. Scoped by construction: it only ever reaches a
     * client built for a profile that turned `allowSelfSigned` on.
     */
    private val TRUST_MANAGER: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
