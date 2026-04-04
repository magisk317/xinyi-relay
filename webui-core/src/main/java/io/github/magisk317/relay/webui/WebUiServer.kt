package io.github.magisk317.relay.webui

import android.content.Context
import io.github.magisk317.relay.common.utils.XLog
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.cio.CIO
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import timber.log.Timber

data class WebUiRuntimeConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val allowLanAccess: Boolean,
    val tlsMaterial: WebUiTlsMaterial,
)

class WebUiServer(
    context: Context,
    private val runtimeConfig: WebUiRuntimeConfig,
) {

    private val appContext = context.applicationContext ?: context
    private var engine: EmbeddedServer<*, *>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val sessionManager = SessionManager()
    private val csrfVerifier = CsrfVerifier(runtimeConfig.allowLanAccess)
    private val rateLimiter = AuthRateLimiter()
    private val dataService = WebUiDataService(appContext)
    private val assetHandler = WebUiAssetHandler(appContext)

    fun start() {
        if (engine != null) return
        val environment = applicationEnvironment {}
        engine = embeddedServer(
            factory = CIO,
            environment = environment,
            configure = {
                sslConnector(
                    keyStore = runtimeConfig.tlsMaterial.keyStore,
                    keyAlias = runtimeConfig.tlsMaterial.keyAlias,
                    keyStorePassword = { runtimeConfig.tlsMaterial.storePassword.toCharArray() },
                    privateKeyPassword = { runtimeConfig.tlsMaterial.keyPassword.toCharArray() },
                ) {
                    host = runtimeConfig.host
                    port = runtimeConfig.port
                }
            },
            module = { configureRoutes() },
        ).start(wait = false)

        Timber.i(
            "WebUI server started at https://%s:%d (lan=%s)",
            runtimeConfig.host,
            runtimeConfig.port,
            runtimeConfig.allowLanAccess,
        )
        XLog.i(
            "WebUI server started host=%s port=%d lan=%s",
            runtimeConfig.host,
            runtimeConfig.port,
            runtimeConfig.allowLanAccess,
        )
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        engine = null
        Timber.i("WebUI server stopped")
        XLog.i("WebUI server stopped")
    }

    private fun io.ktor.server.application.Application.configureRoutes() {
        routing {
            get("/health") {
                call.respondJson(json, HealthResponse(status = "ok"))
            }

            registerAuthRoutes(
                json = json,
                expectedUsername = runtimeConfig.username,
                expectedPassword = runtimeConfig.password,
                dataService = dataService,
                sessionManager = sessionManager,
                csrfVerifier = csrfVerifier,
                rateLimiter = rateLimiter,
            )

            route("/api/v1") {
                registerSettingsRoutes(
                    json = json,
                    dataService = dataService,
                    sessionManager = sessionManager,
                    csrfVerifier = csrfVerifier,
                )
                registerAnalyticsRoutes(
                    json = json,
                    dataService = dataService,
                    sessionManager = sessionManager,
                )
                registerAppRoutes(
                    json = json,
                    dataService = dataService,
                    sessionManager = sessionManager,
                    csrfVerifier = csrfVerifier,
                )
                registerRecordRoutes(
                    json = json,
                    dataService = dataService,
                    sessionManager = sessionManager,
                    csrfVerifier = csrfVerifier,
                )
                registerSenderRoutes(
                    json = json,
                    dataService = dataService,
                    sessionManager = sessionManager,
                    csrfVerifier = csrfVerifier,
                )
            }

            get("/") {
                assetHandler.respond(call)
            }

            get("/{...}") {
                assetHandler.respond(call)
            }
        }
    }
}
