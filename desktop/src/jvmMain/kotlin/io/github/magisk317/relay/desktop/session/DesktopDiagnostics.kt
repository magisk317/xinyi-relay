package io.github.magisk317.relay.desktop.session

import io.github.magisk317.relay.desktop.local.LocalSyncStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Support bundle for the diagnostics export (parity §5, `诊断信息导出`).
 *
 * Mirrors the Tauri `desktop_export_diagnostics` payload: profiles, the
 * connection snapshot, a session summary WITHOUT tokens, and — KMP-specific
 * additions the Rust shell has no need for — the app/platform stamp and the
 * local mirror status. Those two extensions are what a bug report from the
 * KMP track actually needs (version, JVM, os, sync state) and are labelled as
 * such rather than smuggled into the Tauri-shaped sections.
 *
 * The session summary is a hand-picked projection of [DesktopSession]: the
 * tokens live in the same file the rest of the session does, so serializing
 * the session object itself would paste live credentials into a file the
 * user is about to share. [collectDiagnostics] is the only way to build one
 * of these, and a test pins that no token-shaped string survives it.
 */
@Serializable
data class DesktopDiagnostics(
    val createdAt: String,
    val app: DesktopAppInfo,
    val profiles: List<DesktopProfile>,
    /** Always null on the KMP track: the shell has no notification switches. */
    val notifications: NotificationsSection? = null,
    val connection: ConnectionSection,
    val session: DiagnosticsSession? = null,
    val runMode: String,
    val mirror: MirrorSection? = null,
) {
    fun encode(): String = diagnosticsJson.encodeToString(DiagnosticsSerializer, this)

    companion object {
        val DiagnosticsSerializer = serializer()

        fun decode(text: String): DesktopDiagnostics =
            diagnosticsJson.decodeFromString(DiagnosticsSerializer, text)

        private val diagnosticsJson = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            explicitNulls = false
            // encodeDefaults must be explicit: the Kotlin/kotlinx-serialization
            // combination in use does not reliably apply the implicit default,
            // and the section markers are exactly the kind of property that
            // gets dropped when it does not.
            encodeDefaults = true
        }
    }
}

/** Version/platform stamp; the KMP extension section of the payload. */
@Serializable
data class DesktopAppInfo(
    val name: String,
    val version: String,
    val os: String,
    val arch: String,
    val java: String,
)

/** Tauri's `DesktopConnectionSnapshot`: state + human message + last change. */
@Serializable
data class ConnectionSection(
    val state: String,
    val message: String,
    val lastChangedAt: String? = null,
)

/**
 * Token-free view of the active session. The field set matches Tauri's
 * diagnostics session projection exactly (identifier, username, the two
 * expiry stamps) — nothing credential-shaped.
 */
@Serializable
data class DiagnosticsSession(
    val profileId: String,
    val username: String,
    val expiresAt: String,
    val refreshExpiresAt: String,
)

/**
 * Tauri's notification preference block. Always null on the KMP track — the
 * shell has no notification switches yet — but the section keeps the payload
 * shape aligned with the Rust export so a bundle from either track reads the
 * same.
 */
@Serializable
data class NotificationsSection(
    val enabled: Boolean,
    val connection: Boolean,
    val records: Boolean,
    val devices: Boolean,
)

/** KMP extension: the local mirror the Remote run mode does not open. */
@Serializable
data class MirrorSection(
    val active: Boolean,
    val devices: Int,
    val records: Int,
    val syncing: Boolean,
    val error: String? = null,
)

/**
 * Builds a diagnostics snapshot from the shell's live state.
 *
 * [session] supplies everything the shell knows; [mirror] is the footer
 * status (null before the first round or in Remote mode). The session
 * summary is read through [DesktopSessionState.currentSessionMeta] so the
 * expiry stamps are the on-disk truth at export time, after any refresh.
 */
fun collectDiagnostics(
    session: DesktopSessionState,
    mirror: LocalSyncStatus?,
    clock: () -> Instant = Instant::now,
): DesktopDiagnostics {
    val profile = session.activeProfile
    return DesktopDiagnostics(
        createdAt = clock().toString(),
        app = DesktopAppInfo(
            name = DIAGNOSTICS_APP_NAME,
            version = appVersion(),
            os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
            arch = System.getProperty("os.arch"),
            java = System.getProperty("java.version"),
        ),
        profiles = session.loadedProfiles,
        notifications = null,
        connection = ConnectionSection(
            state = when {
                // The Rust monitor pins "local" with an empty message under
                // the Local run mode (main.rs, monitor_tick / mode switch).
                session.runMode == DesktopRunMode.Local -> "local"
                !session.connected -> "disconnected"
                session.authenticated -> "connected"
                else -> "unauthenticated"
            },
            message = profile?.baseUrl.orEmpty(),
        ),
        session = session.currentSessionMeta(),
        runMode = session.runMode.name,
        mirror = mirror?.let {
            MirrorSection(
                active = session.runMode.usesLocalMirror,
                devices = it.devices,
                records = it.records,
                syncing = it.syncing,
                error = it.error,
            )
        },
    )
}

/** App display name, matching the distribution `packageName`. */
const val DIAGNOSTICS_APP_NAME: String = "xinyi-relay-desktop-kmp"

/** Jar manifest version when packaged; "dev" from a plain classpath run. */
private fun appVersion(): String =
    DesktopDiagnostics::class.java.`package`?.implementationVersion?.takeIf { it.isNotBlank() } ?: "dev"

/** `xinyi-relay-diagnostics-<UTC yyyyMMdd-HHmmss>.json`. */
fun diagnosticsFileName(clock: () -> Instant = Instant::now): String {
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(clock().atOffset(ZoneOffset.UTC))
    return "xinyi-relay-diagnostics-$stamp.json"
}
