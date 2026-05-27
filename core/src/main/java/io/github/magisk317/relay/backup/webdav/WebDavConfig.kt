package io.github.magisk317.relay.backup.webdav

import kotlinx.serialization.Serializable

@Serializable
data class WebDavConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val remotePath: String = "/xinyi-relay/backups/",
) {
    val baseUrl: String
        get() = serverUrl.trimEnd('/')

    fun getFullUrl(fileName: String): String {
        val path = remotePath.trimStart('/').trimEnd('/')
        return "$baseUrl/$path/$fileName"
    }

    fun getDirectoryUrl(): String {
        val path = remotePath.trimStart('/').trimEnd('/')
        return "$baseUrl/$path/"
    }
}
