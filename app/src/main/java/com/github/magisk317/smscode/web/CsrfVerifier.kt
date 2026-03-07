package com.github.magisk317.smscode.web

import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall

internal class CsrfVerifier {

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
        val allowedHosts = setOf(requestHost, "127.0.0.1", "localhost")

        val originHost = parseHost(call.request.headers["Origin"])
        val refererHost = parseHost(call.request.headers["Referer"])

        if (originHost == null && refererHost == null) {
            return false
        }
        if (originHost != null && originHost !in allowedHosts) {
            return false
        }
        if (refererHost != null && refererHost !in allowedHosts) {
            return false
        }
        return true
    }

    private fun parseHost(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Url(value).host.lowercase() }.getOrNull()
    }
}
