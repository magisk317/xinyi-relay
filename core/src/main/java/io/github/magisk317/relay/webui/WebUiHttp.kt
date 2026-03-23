package io.github.magisk317.relay.webui

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

suspend inline fun <reified T> ApplicationCall.respondJson(
    json: Json,
    payload: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondText(
        status = status,
        text = json.encodeToString(payload),
        contentType = ContentType.Application.Json,
    )
}

suspend fun ApplicationCall.respondError(
    json: Json,
    status: HttpStatusCode,
    message: String,
) {
    respondJson(
        json = json,
        payload = ErrorResponse(error = message),
        status = status,
    )
}

suspend inline fun <reified T> ApplicationCall.receivePayload(json: Json): T {
    return json.decodeFromString(receiveText())
}

suspend fun ApplicationCall.requireSession(
    json: Json,
    sessionManager: SessionManager,
): WebUiSession? {
    val session = sessionManager.get(request.cookies[SessionManager.COOKIE_NAME])
    if (session == null) {
        respondError(json, HttpStatusCode.Unauthorized, "Unauthorized")
        return null
    }
    return session
}

suspend fun ApplicationCall.requireWriteGuard(
    json: Json,
    session: WebUiSession,
    csrfVerifier: CsrfVerifier,
): Boolean {
    if (!csrfVerifier.isOriginAllowed(this)) {
        respondError(json, HttpStatusCode.Forbidden, "Origin not allowed")
        return false
    }
    if (!csrfVerifier.isCsrfTokenValid(this, session)) {
        respondError(json, HttpStatusCode.Forbidden, "Invalid CSRF token")
        return false
    }
    return true
}

@Serializable
internal data class EmptyResponse(val ok: Boolean = true)
