package io.github.magisk317.relay.web

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.date.GMTDate
import kotlinx.serialization.json.Json

internal fun Route.registerAuthRoutes(
    json: Json,
    runtimeConfig: WebUiRuntimeConfig,
    sessionManager: SessionManager,
    csrfVerifier: CsrfVerifier,
    rateLimiter: AuthRateLimiter,
) {
    route("/auth") {
        post("/login") {
            if (!csrfVerifier.isOriginAllowed(call)) {
                call.respondError(json, HttpStatusCode.Forbidden, "Origin not allowed")
                return@post
            }
            val payload = runCatching { call.receivePayload<LoginPayload>(json) }.getOrElse {
                call.respondError(json, HttpStatusCode.BadRequest, "Invalid JSON payload")
                return@post
            }

            val remoteHost = call.request.local.remoteHost
            if (rateLimiter.isBlocked(remoteHost)) {
                call.respondError(json, HttpStatusCode.TooManyRequests, "Too many attempts, try later")
                return@post
            }

            val expectedUsername = runtimeConfig.username.trim()
            val expectedPassword = runtimeConfig.password
            val valid = expectedUsername.isNotBlank() && expectedPassword.isNotBlank() &&
                payload.username.trim() == expectedUsername && payload.password == expectedPassword
            if (!valid) {
                rateLimiter.recordFailure(remoteHost)
                call.respondError(json, HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            }
            rateLimiter.recordSuccess(remoteHost)

            val session = sessionManager.create(expectedUsername)
            call.response.cookies.append(
                Cookie(
                    name = SessionManager.COOKIE_NAME,
                    value = session.id,
                    path = "/",
                    maxAge = SessionManager.COOKIE_MAX_AGE_SECONDS,
                    secure = true,
                    httpOnly = true,
                    extensions = mapOf("SameSite" to "Strict"),
                ),
            )
            call.respondJson(
                json = json,
                payload = LoginResponse(
                    authenticated = true,
                    username = session.username,
                    csrfToken = session.csrfToken,
                ),
            )
        }

        get("/me") {
            val session = sessionManager.get(call.request.cookies[SessionManager.COOKIE_NAME])
            if (session == null) {
                call.respondJson(
                    json = json,
                    payload = MeResponse(authenticated = false),
                )
                return@get
            }
            call.respondJson(
                json = json,
                payload = MeResponse(
                    authenticated = true,
                    username = session.username,
                    csrfToken = session.csrfToken,
                ),
            )
        }

        post("/logout") {
            val session = call.requireSession(json, sessionManager) ?: return@post
            if (!call.requireWriteGuard(json, session, csrfVerifier)) {
                return@post
            }
            sessionManager.destroy(session.id)
            call.response.cookies.append(
                Cookie(
                    name = SessionManager.COOKIE_NAME,
                    value = "",
                    path = "/",
                    maxAge = 0,
                    expires = GMTDate.START,
                    secure = true,
                    httpOnly = true,
                    extensions = mapOf("SameSite" to "Strict"),
                ),
            )
            call.respondJson(json, SimpleOkResponse(ok = true))
        }
    }
}
