package io.github.magisk317.relay.desktop.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.magisk317.relay.contract.remote.MeResponse
import io.github.magisk317.relay.desktop.auth.DesktopAuthFlow
import io.github.magisk317.relay.desktop.remote.ConsoleClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * UI-facing session controller for the desktop shell, mirroring the webUI
 * AuthProvider (loading / connected / authenticated / username) on top of the
 * C2 primitives: ProfileStore keeps the active backend profile, SessionManager
 * keeps its ConsoleClient token in sync, and login runs the browser-handoff
 * flow (the same one the Rust desktop uses).
 */
class DesktopSessionState(private val store: ProfileStore = ProfileStore()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionManager = SessionManager(store)

    /** Bootstrap in progress (session probe on startup). */
    var loading by mutableStateOf(true)
        private set

    /** Backend reachable and the session probe answered. */
    var connected by mutableStateOf(false)
        private set

    var authenticated by mutableStateOf(false)
        private set

    var username by mutableStateOf("")
        private set

    /** False when the backend has no admin account initialized yet. */
    var adminInitialized by mutableStateOf(true)
        private set

    var activeProfile by mutableStateOf<DesktopProfile?>(null)
        private set

    /**
     * Every profile in the state file, read at bootstrap and updated by
     * [saveProfile]. The diagnostics export needs the full list; reading it
     * here keeps the store private to this class.
     */
    var loadedProfiles by mutableStateOf<List<DesktopProfile>>(emptyList())
        private set

    /**
     * Which backend the app talks to (Remote / Local / Hybrid), persisted in
     * the state file; see [DesktopRunMode]. Loaded in [bootstrap] and updated
     * through [switchRunMode], both off the same `profiles.json` the profiles
     * live in.
     */
    var runMode by mutableStateOf(DesktopRunMode.Default)
        private set

    /** Language tag reported by the backend session; drives "system" locale. */
    var serverLanguageTag by mutableStateOf("")
        private set

    var loginBusy by mutableStateOf(false)
        private set

    private var client: ConsoleClient? = null

    fun currentClient(): ConsoleClient? = client

    fun bootstrap() {
        scope.launch {
            loading = true
            val state = store.loadState()
            // Surfaced before the early return below: the mode must be known
            // even when no session is stored, so the mirror assembly gate
            // observes it on the login screen too.
            runMode = state.runMode
            loadedProfiles = state.profiles
            val profile = state.activeProfileId?.let { id -> state.profiles.firstOrNull { it.id == id } }
            val persisted = profile?.let { store.loadSession(it.id) }
            if (profile == null || persisted == null) {
                loading = false
                return@launch
            }
            val consoleClient = sessionManager.clientFor(profile)
            client = consoleClient
            activeProfile = profile
            val probe = runCatching {
                sessionManager.refreshIfExpiring(profile, consoleClient)
                consoleClient.me()
            }
            probe
                .onSuccess { applyMe(it) }
                .onFailure {
                    connected = false
                    authenticated = false
                    username = ""
                    store.clearSession(profile.id)
                }
            loading = false
            probeAdmin()
        }
    }

    /**
     * Switches the run mode and persists it; [runMode] publishes immediately.
     * The shell re-assembles the local mirror off it (Local/Hybrid open and
     * sync, Remote closes), the same load-at-boot / save-on-change shape the
     * locale preference uses.
     */
    fun switchRunMode(mode: DesktopRunMode) {
        if (runMode == mode) return
        runMode = mode
        store.saveState(store.loadState().copy(runMode = mode))
    }

    /**
     * Token-free projection of the active session, read from the store so the
     * expiry stamps reflect any refresh that already ran. Returns null before
     * login or after logout; the diagnostics export refuses to carry tokens,
     * so the session file's access/refresh tokens never leave this method.
     */
    fun currentSessionMeta(): DiagnosticsSession? {
        val profile = activeProfile ?: return null
        val session = store.loadSession(profile.id) ?: return null
        return DiagnosticsSession(
            profileId = session.profileId,
            username = session.username,
            expiresAt = session.expiresAt,
            refreshExpiresAt = session.refreshExpiresAt,
        )
    }

    private fun applyMe(me: MeResponse) {
        connected = true
        authenticated = me.authenticated
        username = me.username.orEmpty()
        serverLanguageTag = me.languageTag.orEmpty()
    }

    private suspend fun probeAdmin() {
        val baseUrl = activeProfile?.baseUrl ?: return
        val info = runCatching { ConsoleClient(baseUrl).systemInfo() }.getOrNull() ?: return
        adminInitialized = info.userCount > 0
    }

    /** Creates or updates the active backend profile; returns it. */
    fun saveProfile(baseUrl: String, name: String = DEFAULT_PROFILE_NAME): DesktopProfile {
        val normalized = baseUrl.trim().trimEnd('/')
        val state = store.loadState()
        val existing = state.profiles.firstOrNull { it.baseUrl == normalized }
        val profile = existing?.copy(name = name) ?: DesktopProfile(
            id = "profile-${state.profiles.size + 1}",
            name = name,
            baseUrl = normalized,
        )
        val profiles = if (existing == null) state.profiles + profile else state.profiles.map { if (it.id == profile.id) profile else it }
        store.saveState(state.copy(profiles = profiles, activeProfileId = profile.id))
        loadedProfiles = profiles
        activeProfile = profile
        return profile
    }

    /**
     * Runs the browser-handoff login against the active profile: opens the
     * backend start URL in the system browser, waits for the loopback
     * callback, exchanges the code and persists the session.
     */
    fun signIn(onResult: (Result<Unit>) -> Unit) {
        if (loginBusy) return
        loginBusy = true
        scope.launch {
            val result = runCatching {
                val profile = activeProfile ?: error("no active profile")
                val consoleClient = client ?: sessionManager.clientFor(profile).also { client = it }
                val flow = DesktopAuthFlow()
                try {
                    flow.authUrl(profile.baseUrl).let { url ->
                        if (!flow.openBrowser(url)) error("cannot open the system browser")
                    }
                    flow.start()
                    val response = flow.completeLogin(consoleClient)
                    sessionManager.onAuthenticated(profile, response)
                    connected = true
                    authenticated = response.authenticated
                    username = response.username
                } finally {
                    flow.close()
                }
                probeAdmin()
            }
            loginBusy = false
            // Snapshot state writes are thread-safe; no Main dispatcher needed here.
            onResult(result)
        }
    }

    fun logout() {
        scope.launch {
            val profile = activeProfile ?: return@launch
            client?.let { sessionManager.logout(profile, it) }
            connected = false
            authenticated = false
            username = ""
        }
    }

    private companion object {
        const val DEFAULT_PROFILE_NAME = "default"
    }
}
