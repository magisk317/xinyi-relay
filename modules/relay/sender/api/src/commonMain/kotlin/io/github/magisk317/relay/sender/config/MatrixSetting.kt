package io.github.magisk317.relay.sender.config

import io.github.magisk317.relay.contract.model.ProxyType
import kotlinx.serialization.Serializable as KotlinSerializable

@KotlinSerializable
data class MatrixSetting(
    val homeserver: String = "https://matrix.org",
    @Deprecated("Use username+password login instead. Kept for plaintext fallback and RoomCryptoState queries.")
    val accessToken: String = "",
    val roomId: String = "",
    val messageType: String = "text",
    val titleTemplate: String = "",
    /** Matrix username (e.g. "@user:matrix.org" or just "user"). */
    val username: String = "",
    /** Matrix password. Used with [username] for login to obtain a stable device session. */
    val password: String = "",
    @KotlinSerializable(with = ProxyTypeSerializer::class)
    val proxyType: ProxyType = ProxyType.DIRECT,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyAuthenticator: Boolean = false,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
)
