package io.github.magisk317.relay.web

import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import java.net.InetAddress

internal class CsrfVerifier(
    private val allowLanAccess: Boolean,
) {

    fun isCsrfTokenValid(call: ApplicationCall, session: WebUiSession): Boolean {
        val csrfToken = call.request.headers["X-CSRF-Token"]?.trim().orEmpty()
        return csrfToken.isNotBlank() && csrfToken == session.csrfToken
    }

    fun isOriginAllowed(call: ApplicationCall): Boolean {
        val requestHost = call.request.headers["Host"]
            ?.substringBefore(':')
            ?.lowercase()
            .orEmpty()
            .ifBlank { "127.0.0.1" }
        val originHost = parseHost(call.request.headers["Origin"])
        val refererHost = parseHost(call.request.headers["Referer"])

        if (!allowLanAccess) {
            if (originHost == null && refererHost == null) {
                return false
            }
            if (originHost != null && !isHostAllowed(originHost, requestHost, allowLanAccess = false)) {
                return false
            }
            if (refererHost != null && !isHostAllowed(refererHost, requestHost, allowLanAccess = false)) {
                return false
            }
            return true
        }

        if (originHost == null && refererHost == null) {
            return true
        }
        if (originHost != null && isHostAllowed(originHost, requestHost, allowLanAccess = true)) {
            return true
        }
        if (refererHost != null && isHostAllowed(refererHost, requestHost, allowLanAccess = true)) {
            return true
        }
        return false
    }

    private fun isHostAllowed(host: String, requestHost: String, allowLanAccess: Boolean): Boolean {
        if (host == requestHost) return true
        if (host == "127.0.0.1" || host == "localhost") return true
        if (!allowLanAccess) return false
        return isPrivateHost(host)
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost") return true
        return runCatching {
            val addr = InetAddress.getByName(host)
            addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress
        }.getOrDefault(false)
    }

    private fun parseHost(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Url(value).host.lowercase() }.getOrNull()
    }
}
