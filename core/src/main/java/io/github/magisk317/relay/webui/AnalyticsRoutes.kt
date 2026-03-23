package io.github.magisk317.relay.webui

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json

fun Route.registerAnalyticsRoutes(
    json: Json,
    dataService: WebUiDataService,
    sessionManager: SessionManager,
) {
    get("/analytics") {
        call.requireSession(json, sessionManager) ?: return@get
        call.respondJson(json, dataService.getAnalytics())
    }
}
