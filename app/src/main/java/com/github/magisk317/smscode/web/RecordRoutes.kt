package com.github.magisk317.smscode.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json

internal fun Route.registerRecordRoutes(
    json: Json,
    dataService: WebUiDataService,
    sessionManager: SessionManager,
    csrfVerifier: CsrfVerifier,
) {
    get("/records") {
        call.requireSession(json, sessionManager) ?: return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
        call.respondJson(json, dataService.getRecords(limit))
    }

    delete("/records/{recordId}") {
        val session = call.requireSession(json, sessionManager) ?: return@delete
        if (!call.requireWriteGuard(json, session, csrfVerifier)) {
            return@delete
        }

        val recordId = call.parameters["recordId"]?.toLongOrNull()
        if (recordId == null) {
            call.respondError(json, HttpStatusCode.BadRequest, "Invalid recordId")
            return@delete
        }
        val deleted = dataService.deleteRecord(recordId)
        if (!deleted) {
            call.respondError(json, HttpStatusCode.NotFound, "Record not found")
            return@delete
        }
        call.respondJson(json, SimpleOkResponse(ok = true))
    }
}
