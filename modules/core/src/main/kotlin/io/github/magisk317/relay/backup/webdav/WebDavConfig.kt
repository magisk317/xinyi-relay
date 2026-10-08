package io.github.magisk317.relay.backup.webdav

import kotlinx.serialization.Serializable

@Serializable
data class WebDavConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val remotePath: String = DEFAULT_REMOTE_PATH,
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

    fun getDirectoryUrl(path: String): String {
        val normalizedPath = path.trimStart('/').trimEnd('/')
        return "$baseUrl/$normalizedPath/"
    }

    companion object {
        const val DEFAULT_SERVER_URL = "https://dav.jianguoyun.com/dav/"
        const val DEFAULT_REMOTE_PATH = "/xinyi-relay/backups/"
    }
}
