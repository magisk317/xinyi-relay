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
data class PersistedDesktopState(
    val profiles: List<DesktopProfile> = emptyList(),
    val activeProfileId: String? = null,
)

/**
 * File-backed profile/session persistence (~/.xinyi-relay-desktop), the JVM
 * counterpart of the Rust storage.rs + keyring pair. Session files are
 * written 0600; a keyring-backed actual can replace this later without
 * touching callers.
 */
class ProfileStore(private val dir: Path = defaultDir()) {
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
        return runCatching { json.decodeFromString<DesktopSession>(Files.readString(f)) }.getOrNull()
    }

    fun saveSession(session: DesktopSession) {
        writePrivate(sessionFile(session.profileId), json.encodeToString(DesktopSession.serializer(), session))
    }

    fun clearSession(profileId: String) {
        runCatching { Files.deleteIfExists(sessionFile(profileId)) }
    }

    private fun writePrivate(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        runCatching { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")) }
    }
}
