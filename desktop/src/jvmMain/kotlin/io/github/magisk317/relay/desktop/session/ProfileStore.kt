package io.github.magisk317.relay.desktop.session

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DesktopProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val allowSelfSigned: Boolean = false,
)

@Serializable
data class DesktopSession(
    val profileId: String,
    val username: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val refreshExpiresAt: String,
)

@Serializable
private data class PersistedSessionMetadata(
    val profileId: String,
    val username: String,
    val expiresAt: String,
    val refreshExpiresAt: String,
)

@Serializable
data class PersistedDesktopState(
    val profiles: List<DesktopProfile> = emptyList(),
    val activeProfileId: String? = null,
    val runMode: DesktopRunMode = DesktopRunMode.Default,
)

/**
 * File-backed profile metadata plus native-keyring session persistence
 * (~/.xinyi-relay-desktop), the JVM counterpart of the Rust storage.rs +
 * keyring pair. Session files contain only the username and expiry stamps;
 * bearer tokens live in [DesktopCredentialStore] and never enter JSON.
 */
class ProfileStore(
    private val dir: Path = defaultDir(),
    private val credentials: DesktopCredentialStore = SystemCredentialStore(),
) {
    companion object {
        fun defaultDir(): Path = Paths.get(System.getProperty("user.home"), ".xinyi-relay-desktop")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val stateFile: Path get() = dir.resolve("profiles.json")
    private fun sessionFile(profileId: String): Path = dir.resolve("session-$profileId.json")

    fun loadState(): PersistedDesktopState {
        val f = stateFile
        if (!Files.exists(f)) return PersistedDesktopState()
        return runCatching { json.decodeFromString<PersistedDesktopState>(Files.readString(f)) }
            .getOrDefault(PersistedDesktopState())
    }

    fun saveState(state: PersistedDesktopState) {
        writePrivate(stateFile, json.encodeToString(PersistedDesktopState.serializer(), state))
    }

    fun loadSession(profileId: String): DesktopSession? {
        val f = sessionFile(profileId)
        if (!Files.exists(f)) return null
        val raw = runCatching { Files.readString(f) }.getOrNull() ?: return null

        // One-time migration for the original plaintext session format. The
        // old file is only replaced after both secrets have reached the native
        // store; a keychain failure therefore does not destroy recoverable data.
        runCatching { json.decodeFromString<DesktopSession>(raw) }.getOrNull()?.let { legacy ->
            runCatching {
                credentials.save(
                    profileId,
                    DesktopCredentials(legacy.accessToken, legacy.refreshToken),
                )
                writeMetadata(legacy)
            }.onFailure { return null }
            return legacy
        }

        val metadata = runCatching { json.decodeFromString<PersistedSessionMetadata>(raw) }.getOrNull()
            ?: return null
        val secret = runCatching { credentials.load(profileId) }.getOrNull() ?: return null
        return DesktopSession(
            profileId = metadata.profileId,
            username = metadata.username,
            accessToken = secret.accessToken,
            refreshToken = secret.refreshToken,
            expiresAt = metadata.expiresAt,
            refreshExpiresAt = metadata.refreshExpiresAt,
        )
    }

    fun saveSession(session: DesktopSession) {
        credentials.save(
            session.profileId,
            DesktopCredentials(session.accessToken, session.refreshToken),
        )
        writeMetadata(session)
    }

    fun clearSession(profileId: String) {
        credentials.clear(profileId)
        runCatching { Files.deleteIfExists(sessionFile(profileId)) }
    }

    private fun writeMetadata(session: DesktopSession) {
        writePrivate(
            sessionFile(session.profileId),
            json.encodeToString(
                PersistedSessionMetadata.serializer(),
                PersistedSessionMetadata(
                    profileId = session.profileId,
                    username = session.username,
                    expiresAt = session.expiresAt,
                    refreshExpiresAt = session.refreshExpiresAt,
                ),
            ),
        )
    }

    private fun writePrivate(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        runCatching { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")) }
    }
}
