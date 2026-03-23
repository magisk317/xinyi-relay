package io.github.magisk317.relay.webui

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch

import kotlinx.serialization.json.Json

fun Route.registerAppRoutes(
    json: Json,
    dataService: WebUiDataService,
    sessionManager: SessionManager,
    csrfVerifier: CsrfVerifier,
) {
    get("/apps") {
        call.requireSession(json, sessionManager) ?: return@get
        call.respondJson(json, dataService.loadMergedAppItems())
    }

    patch("/apps/{packageName}") {
        val session = call.requireSession(json, sessionManager) ?: return@patch
        if (!call.requireWriteGuard(json, session, csrfVerifier)) {
            return@patch
        }

        val packageName = call.parameters["packageName"]?.trim().orEmpty()
        if (packageName.isBlank()) {
            call.respondError(json, HttpStatusCode.BadRequest, "Missing packageName")
            return@patch
        }

        val payload = runCatching { call.receivePayload<AppUpdatePayload>(json) }.getOrElse {
            call.respondError(json, HttpStatusCode.BadRequest, "Invalid JSON payload")
            return@patch
        }

        if (payload.blocked == null && payload.forwarding == null && payload.notifyTemplate == null) {
            call.respondError(json, HttpStatusCode.BadRequest, "No changes provided")
            return@patch
        }

        val updated = dataService.updateApp(packageName, payload)
        call.respondJson(json, updated)
    }
}
