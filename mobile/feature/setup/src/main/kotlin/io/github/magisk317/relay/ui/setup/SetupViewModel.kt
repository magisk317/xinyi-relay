package io.github.magisk317.relay.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.domain.recovery.RootShell
import io.github.magisk317.relay.mobilefeature.setup.BuildConfig
import io.github.magisk317.relay.ui.permission.PermissionRequirement
import io.github.magisk317.relay.ui.permission.StartupPermissionCollector
import io.github.magisk317.relay.ui.permission.StartupPermissionConfig
import io.github.magisk317.relay.ui.permission.StartupPromptStore
import io.github.magisk317.relay.ui.permission.runRootGrants
import io.github.magisk317.relay.ui.permission.startupPermissionConfig
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One page of the root-first setup wizard.
 *
 * The order is deliberate: privacy first (nothing else may run before the user
 * agrees), then root, then whatever root cannot cover. [DONE] is terminal - the
 * caller leaves the wizard from there.
 */
enum class SetupStep {
    WELCOME,
    ROOT,
    GRANT_ROOT,
    GRANT_MANUAL,
    DONE,
}

data class SetupUiState(
    val step: SetupStep = SetupStep.WELCOME,
    val rootProbing: Boolean = false,
    val rootAvailable: Boolean = false,
    val granting: Boolean = false,
    val grantLog: List<String> = emptyList(),
    val missing: List<PermissionRequirement> = emptyList(),
    val grantedCount: Int = 0,
) {
    /** Runtime permissions still missing, requested through the permission launcher. */
    val missingRuntime: List<PermissionRequirement>
        get() = missing.filterNot(PermissionRequirement::isSpecial)

    /** Settings-page permissions still missing, opened one card at a time. */
    val missingSpecial: List<PermissionRequirement>
        get() = missing.filter(PermissionRequirement::isSpecial)
}

/**
 * Drives the setup wizard: privacy acceptance, root probing, the one-tap root
 * grant, and the manual leftovers.
 *
 * Every blocking step (root probe, root grant, permission collection) runs on
 * [Dispatchers.IO]; the state flow is the only thing the screen observes, so the
 * wizard survives recomposition without re-running grants.
 */
class SetupViewModel(
    application: Application,
    private val settingsRepository: SettingsPreferencesRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private var config: StartupPermissionConfig? = null

    /** Permissions missing when the grant phase started, for the done summary. */
    private var grantPhaseTotal = 0

    private fun context(): Application = getApplication()

    private fun ensureConfig(): StartupPermissionConfig =
        config ?: startupPermissionConfig(context(), BuildConfig.ENABLE_ACCESSIBILITY_AUTO_INPUT)
            .also { config = it }

    /** Persists the privacy decision and moves to the root probe. */
    fun acceptPrivacyPolicy() {
        viewModelScope.launch {
            settingsRepository.setPrivacyPolicyAccepted(true)
            _uiState.update { it.copy(step = SetupStep.ROOT) }
            probeRoot()
        }
    }

    /** Persists the refusal; the caller finishes the activity. */
    fun declinePrivacyPolicy(onDeclined: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setPrivacyPolicyAccepted(false)
            onDeclined()
        }
    }

    /** Probes root without leaving the root step; the user still confirms. */
    fun probeRoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(rootProbing = true) }
            val rootAvailable = withContext(Dispatchers.IO) {
                runCatching { RootShell.canUseRoot() }.getOrDefault(false)
            }
            _uiState.update { it.copy(rootProbing = false, rootAvailable = rootAvailable) }
        }
    }

    /** Enters the grant phase, skipping it when nothing is missing. */
    fun proceedToGrants() {
        viewModelScope.launch {
            val missing = collectMissing()
            grantPhaseTotal = missing.size
            if (missing.isEmpty()) {
                completeSetup()
                return@launch
            }
            _uiState.update {
                it.copy(step = if (it.rootAvailable) SetupStep.GRANT_ROOT else SetupStep.GRANT_MANUAL)
            }
        }
    }

    /** One-tap grant through the root bridge, then re-collect what is left. */
    fun grantViaRoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(granting = true, grantLog = emptyList()) }
            val cfg = ensureConfig()
            withContext(Dispatchers.IO) {
                runCatching {
                    runRootGrants(context(), cfg) { message ->
                        _uiState.update { it.copy(grantLog = it.grantLog + message) }
                        XLog.w(message = "PermissionBridge", message)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    XLog.w(message = "Setup root grant failed", error)
                }
            }
            val remaining = collectMissing()
            _uiState.update {
                it.copy(
                    granting = false,
                    step = if (remaining.isEmpty()) SetupStep.DONE else SetupStep.GRANT_MANUAL,
                )
            }
        }
    }

    /** Re-reads the missing set after a runtime/settings grant came back. */
    fun refreshMissing() {
        viewModelScope.launch { collectMissing() }
    }

    /** Leaves the wizard; the version-level marker keeps the legacy prompt quiet. */
    fun finishManualGrants() {
        viewModelScope.launch { completeSetup() }
    }

    fun backStep() {
        _uiState.update { current ->
            val previous = when (current.step) {
                SetupStep.ROOT -> SetupStep.WELCOME
                SetupStep.GRANT_ROOT -> SetupStep.ROOT
                SetupStep.GRANT_MANUAL -> SetupStep.ROOT
                SetupStep.WELCOME, SetupStep.DONE -> current.step
            }
            current.copy(step = previous)
        }
    }

    private suspend fun collectMissing(): List<PermissionRequirement> {
        val missing = withContext(Dispatchers.IO) {
            runCatching {
                StartupPermissionCollector.collectMissing(context(), ensureConfig())
            }.getOrDefault(emptyList())
        }
        _uiState.update { it.copy(missing = missing) }
        return missing
    }

    private suspend fun completeSetup() {
        withContext(Dispatchers.IO) {
            StartupPromptStore.markPromptedForCurrentVersion(context())
        }
        _uiState.update {
            it.copy(
                step = SetupStep.DONE,
                grantedCount = maxOf(0, grantPhaseTotal - it.missing.size),
            )
        }
    }
}
