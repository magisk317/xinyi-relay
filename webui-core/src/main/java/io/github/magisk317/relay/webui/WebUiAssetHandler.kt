package io.github.magisk317.relay.webui

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText

internal class WebUiAssetHandler(context: Context) {

    private val appContext = context.applicationContext ?: context

    suspend fun respond(call: ApplicationCall) {
        val requestPath = call.request.path().trim()
        val normalizedPath = sanitizePath(requestPath) ?: run {
            call.respondText(status = HttpStatusCode.BadRequest, text = "Invalid path")
            return
        }

        val targetPath = when {
            normalizedPath.isBlank() -> "index.html"
            normalizedPath.contains('.') -> normalizedPath
            else -> "index.html"
        }

        val assetPath = "webui/$targetPath"
        val bytes = runCatching {
            appContext.assets.open(assetPath).use { it.readBytes() }
        }.getOrElse {
            if (targetPath != "index.html") {
                call.respondText(status = HttpStatusCode.NotFound, text = "Not found")
            } else {
                call.respondText(status = HttpStatusCode.ServiceUnavailable, text = "WebUI assets missing")
            }
            return
        }

        val contentType = guessContentType(targetPath)
        if (targetPath == "index.html") {
            call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        } else {
            call.response.headers.append(HttpHeaders.CacheControl, "public,max-age=31536000,immutable")
        }
        call.respondBytes(bytes = bytes, contentType = contentType)
    }

    private fun sanitizePath(requestPath: String): String? {
        if (requestPath.contains("..")) return null
        if (requestPath == "/") return ""
        return requestPath.trimStart('/')
    }

    private fun guessContentType(path: String): ContentType {
        return when {
            path.endsWith(".html") -> ContentType.Text.Html
            path.endsWith(".css") -> ContentType.Text.CSS
            path.endsWith(".js") || path.endsWith(".mjs") -> ContentType.Application.JavaScript
            path.endsWith(".json") -> ContentType.Application.Json
            path.endsWith(".svg") -> ContentType.Image.SVG
            path.endsWith(".png") -> ContentType.Image.PNG
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> ContentType.Image.JPEG
            path.endsWith(".ico") -> ContentType.Image.XIcon
            path.endsWith(".txt") -> ContentType.Text.Plain
            path.endsWith(".map") -> ContentType.Application.Json
            path.endsWith(".woff2") -> ContentType.parse("font/woff2")
            else -> ContentType.Application.OctetStream
        }
    }
}
