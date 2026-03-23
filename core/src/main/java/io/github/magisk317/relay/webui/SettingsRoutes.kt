package io.github.magisk317.relay.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import kotlinx.serialization.json.Json

fun Route.registerSettingsRoutes(
    json: Json,
    dataService: WebUiDataService,
    sessionManager: SessionManager,
    csrfVerifier: CsrfVerifier,
) {
    get("/overview") {
        call.requireSession(json, sessionManager) ?: return@get
        call.respondJson(json, dataService.getOverview())
    }

    get("/settings") {
        call.requireSession(json, sessionManager) ?: return@get
        call.respondJson(json, dataService.getSettingsState())
    }

    patch("/settings") {
        val session = call.requireSession(json, sessionManager) ?: return@patch
        if (!call.requireWriteGuard(json, session, csrfVerifier)) {
            return@patch
        }
        val payload = runCatching { call.receivePayload<SettingsUpdatePayload>(json) }.getOrElse {
            call.respondError(json, HttpStatusCode.BadRequest, "Invalid JSON payload")
            return@patch
        }
        if (
            payload.moduleEnabled == null &&
            payload.verificationFeaturesEnabled == null &&
            payload.relayFeaturesEnabled == null &&
            payload.copyToClipboard == null &&
            payload.showToast == null &&
            payload.showCodeNotification == null &&
            payload.blockSmsEnabled == null &&
            payload.enableAutoInputCode == null &&
            payload.enableAutoEnterCode == null &&
            payload.verboseLogMode == null &&
            payload.smsBlacklistEnabled == null &&
            payload.forceStopRecoveryEnabled == null
        ) {
            call.respondError(json, HttpStatusCode.BadRequest, "No changes provided")
            return@patch
        }
        call.respondJson(json, dataService.updateSettings(payload))
    }

    get("/advanced") {
        call.requireSession(json, sessionManager) ?: return@get
        call.respondJson(json, dataService.getAdvancedState())
    }

    patch("/advanced") {
        val session = call.requireSession(json, sessionManager) ?: return@patch
        if (!call.requireWriteGuard(json, session, csrfVerifier)) {
            return@patch
        }
        val payload = runCatching { call.receivePayload<AdvancedUpdatePayload>(json) }.getOrElse {
            call.respondError(json, HttpStatusCode.BadRequest, "Invalid JSON payload")
            return@patch
        }
        if (payload.enableSmsBlacklist == null && payload.webUiLanAccess == null) {
            call.respondError(json, HttpStatusCode.BadRequest, "No changes provided")
            return@patch
        }
        call.respondJson(json, dataService.updateAdvanced(payload))
    }

    get("/intercept") {
        call.requireSession(json, sessionManager) ?: return@get
        call.respondJson(json, dataService.getInterceptState())
    }

    patch("/intercept") {
        val session = call.requireSession(json, sessionManager) ?: return@patch
        if (!call.requireWriteGuard(json, session, csrfVerifier)) {
            return@patch
        }
        val payload = runCatching { call.receivePayload<InterceptUpdatePayload>(json) }.getOrElse {
            call.respondError(json, HttpStatusCode.BadRequest, "Invalid JSON payload")
            return@patch
        }
        if (
            payload.smsBlacklistNumbers == null &&
            payload.smsBlacklistPrefixes == null &&
            payload.smsBlacklistRegex == null &&
            payload.smsBlacklistContent == null &&
            payload.smsBlacklistActionDelete == null &&
            payload.smsBlacklistActionBlock == null
        ) {
            call.respondError(json, HttpStatusCode.BadRequest, "No changes provided")
            return@patch
        }
        call.respondJson(json, dataService.updateIntercept(payload))
    }

    get("/version") {
        call.requireSession(json, sessionManager) ?: return@get
        call.respondJson(json, dataService.getVersionState())
    }
}
