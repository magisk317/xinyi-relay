package io.github.magisk317.relay.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.domain.recovery.RootShell
import io.github.magisk317.relay.mobilefeature.setup.BuildConfig
import io.github.magisk317.relay.ui.permission.PermissionRequirement
import io.github.magisk317.relay.ui.permission.PermissionStatus
import io.github.magisk317.relay.ui.permission.StartupPermissionCollector
import io.github.magisk317.relay.ui.permission.StartupPermissionConfig
import io.github.magisk317.relay.ui.permission.StartupPromptStore
import io.github.magisk317.relay.ui.permission.SetupProgress
import io.github.magisk317.relay.ui.permission.SetupProgressStore
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
 * One page of the setup wizard.
 *
 * The order is deliberate: privacy first (nothing else may run before the user
 * agrees), then root (optional - skipped when unavailable or declined), then
 * whatever root cannot cover. [DONE] is terminal - the caller leaves the wizard
 * from there.
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
    val permissionStatuses: List<PermissionStatus> = emptyList(),
    val allStatuses: List<PermissionStatus> = emptyList(),
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


    private fun context(): Application = getApplication()

    private fun ensureConfig(): StartupPermissionConfig =
        config ?: startupPermissionConfig(context(), BuildConfig.ENABLE_ACCESSIBILITY_AUTO_INPUT)
            .also { config = it }

    /**
     * Persists the privacy decision, then probes root. Root is optional: when it is
     * unavailable the wizard skips the root step and goes straight to manual grants.
     */
    fun acceptPrivacyPolicy() {
        viewModelScope.launch {
            settingsRepository.setPrivacyPolicyAccepted(true)
            SetupProgressStore.mark(context(), SetupProgress.ROOT_PENDING)
            probeRootAndContinue()
        }
    }

    fun resumeAfterPrivacyPolicy() {
        if (_uiState.value.step != SetupStep.WELCOME) return
        when (SetupProgressStore.current(context())) {
            SetupProgress.GRANT_ROOT -> resumeGrantPhase(forceManual = false)
            SetupProgress.GRANT_MANUAL -> resumeGrantPhase(forceManual = true)
            SetupProgress.COMPLETE_ROOT, SetupProgress.COMPLETE_MANUAL ->
                _uiState.update { it.copy(step = SetupStep.DONE) }
            SetupProgress.ROOT_PENDING, null -> viewModelScope.launch { probeRootAndContinue() }
        }
    }

    private suspend fun probeRootAndContinue() {
        SetupProgressStore.mark(context(), SetupProgress.ROOT_PENDING)
        _uiState.update { it.copy(step = SetupStep.ROOT, rootProbing = true) }
        val rootAvailable = withContext(Dispatchers.IO) {
            runCatching { RootShell.canUseRoot() }.getOrDefault(false)
        }
        _uiState.update { it.copy(rootProbing = false, rootAvailable = rootAvailable) }
        if (!rootAvailable) {
            proceedToGrants(forceManual = true)
        }
    }

    /** Persists the refusal; the caller finishes the activity. */
    fun declinePrivacyPolicy(onDeclined: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setPrivacyPolicyAccepted(false)
            onDeclined()
        }
    }

    /** Enters the grant phase, skipping it when nothing is missing. */
    fun proceedToGrants(forceManual: Boolean = false) {
        viewModelScope.launch {
            publishPermissions()
            val rootSelected = _uiState.value.rootAvailable && !forceManual
            SetupProgressStore.mark(
                context(),
                if (rootSelected) SetupProgress.GRANT_ROOT else SetupProgress.GRANT_MANUAL,
            )
            val missing = _uiState.value.missing
            if (missing.isEmpty()) {
                completeSetup()
                return@launch
            }
            _uiState.update {
                it.copy(step = if (rootSelected) SetupStep.GRANT_ROOT else SetupStep.GRANT_MANUAL)
            }
        }
    }

    /** The user declines the root path; the manual grants still cover everything. */
    fun skipRoot() {
        proceedToGrants(forceManual = true)
    }

    /** One-tap grant through the root bridge, then re-collect what is left. */
    fun grantViaRoot() {
        viewModelScope.launch {
            SetupProgressStore.mark(context(), SetupProgress.GRANT_ROOT)
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
            publishPermissions()
            _uiState.update { it.copy(granting = false) }
            if (_uiState.value.missing.isEmpty()) {
                completeSetup()
            } else {
                SetupProgressStore.mark(context(), SetupProgress.GRANT_MANUAL)
                _uiState.update { it.copy(step = SetupStep.GRANT_MANUAL) }
            }
        }
    }

    /** Re-reads the missing set after a runtime/settings grant came back. */
    fun refreshMissing() {
        viewModelScope.launch { publishPermissions() }
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
                SetupStep.GRANT_MANUAL ->
                    if (current.rootAvailable) SetupStep.ROOT else SetupStep.WELCOME
                SetupStep.WELCOME, SetupStep.DONE -> current.step
            }
            current.copy(step = previous)
        }
    }


    /** Collects the full permission set with live grant state; drives both the
     *  manual grant list and the per-item granted markers. */
    private suspend fun publishPermissions() {
        val statuses = withContext(Dispatchers.IO) {
            runCatching {
                StartupPermissionCollector.collectStatuses(context(), ensureConfig())
            }.getOrDefault(emptyList())
        }
        val missing = statuses.filter { !it.granted }.map { it.requirement }
        _uiState.update { it.copy(missing = missing, allStatuses = statuses) }
    }

    private fun resumeGrantPhase(forceManual: Boolean) {
        viewModelScope.launch {
            val rootAvailable = withContext(Dispatchers.IO) {
                runCatching { RootShell.canUseRoot() }.getOrDefault(false)
            }
            publishPermissions()
            if (_uiState.value.missing.isEmpty()) {
                completeSetup()
                return@launch
            }
            val rootSelected = rootAvailable && !forceManual
            SetupProgressStore.mark(
                context(),
                if (rootSelected) SetupProgress.GRANT_ROOT else SetupProgress.GRANT_MANUAL,
            )
            _uiState.update {
                it.copy(
                    rootAvailable = rootAvailable,
                    step = if (rootSelected) SetupStep.GRANT_ROOT else SetupStep.GRANT_MANUAL,
                )
            }
        }
    }

    private suspend fun completeSetup() {
        val statuses = withContext(Dispatchers.IO) {
            runCatching {
                StartupPermissionCollector.collectStatuses(context(), ensureConfig())
            }.getOrDefault(emptyList())
        }
        withContext(Dispatchers.IO) {
            StartupPromptStore.markPromptedForCurrentVersion(context())
            SetupProgressStore.markComplete(context())
        }
        _uiState.update {
            it.copy(
                step = SetupStep.DONE,
                permissionStatuses = statuses,
                grantedCount = statuses.count { it.granted },
            )
        }
    }
}
