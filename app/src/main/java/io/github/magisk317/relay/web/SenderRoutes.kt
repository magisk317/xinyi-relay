package io.github.magisk317.relay.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json

internal fun Route.registerSenderRoutes(
    json: Json,
    dataService: WebUiDataService,
    sessionManager: SessionManager,
    csrfVerifier: CsrfVerifier,
) {
    get("/senders") {
        call.requireSession(json, sessionManager) ?: return@get
        call.respondJson(json, dataService.getSenders())
    }

    post("/senders") {
        val session = call.requireSession(json, sessionManager) ?: return@post
        if (!call.requireWriteGuard(json, session, csrfVerifier)) {
            return@post
        }

        val payload = runCatching { call.receivePayload<SenderCreatePayload>(json) }.getOrElse {
            call.respondError(json, HttpStatusCode.BadRequest, "Invalid JSON payload")
            return@post
        }
        if (payload.name.trim().isBlank()) {
            call.respondError(json, HttpStatusCode.BadRequest, "Sender name is required")
            return@post
        }
        val created = dataService.createSender(payload)
        if (created == null) {
            call.respondError(json, HttpStatusCode.InternalServerError, "Failed to create sender")
            return@post
        }
        call.respondJson(json, created, HttpStatusCode.Created)
    }

    patch("/senders/{senderId}") {
        val session = call.requireSession(json, sessionManager) ?: return@patch
        if (!call.requireWriteGuard(json, session, csrfVerifier)) {
            return@patch
        }

        val senderId = call.parameters["senderId"]?.toLongOrNull()
        if (senderId == null) {
            call.respondError(json, HttpStatusCode.BadRequest, "Invalid senderId")
            return@patch
        }
        val payload = runCatching { call.receivePayload<SenderUpdatePayload>(json) }.getOrElse {
            call.respondError(json, HttpStatusCode.BadRequest, "Invalid JSON payload")
            return@patch
        }
        val trimmedName = payload.name?.trim()
        if (payload.name != null && trimmedName.isNullOrBlank()) {
            call.respondError(json, HttpStatusCode.BadRequest, "Sender name is required")
            return@patch
        }
        if (
            payload.name == null &&
            payload.type == null &&
            payload.jsonSetting == null &&
            payload.status == null &&
            payload.receiveCode == null &&
            payload.receiveNonCode == null &&
            payload.receiveAppNotify == null &&
            payload.receiveCallNotify == null
        ) {
            call.respondError(json, HttpStatusCode.BadRequest, "No changes provided")
            return@patch
        }
        val updated = dataService.updateSender(senderId, payload.copy(name = trimmedName))
        if (updated == null) {
            call.respondError(json, HttpStatusCode.NotFound, "Sender not found")
            return@patch
        }
        call.respondJson(json, updated)
    }

    delete("/senders/{senderId}") {
        val session = call.requireSession(json, sessionManager) ?: return@delete
        if (!call.requireWriteGuard(json, session, csrfVerifier)) {
            return@delete
        }

        val senderId = call.parameters["senderId"]?.toLongOrNull()
        if (senderId == null) {
            call.respondError(json, HttpStatusCode.BadRequest, "Invalid senderId")
            return@delete
        }
        val deleted = dataService.deleteSender(senderId)
        if (!deleted) {
            call.respondError(json, HttpStatusCode.NotFound, "Sender not found")
            return@delete
        }
        call.respondJson(json, SimpleOkResponse(ok = true))
    }
}
