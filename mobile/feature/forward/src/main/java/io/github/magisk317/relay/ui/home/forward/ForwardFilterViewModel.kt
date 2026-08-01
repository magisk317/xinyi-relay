package io.github.magisk317.relay.ui.home.forward

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.engine.filter.ForwardFilterConst
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ForwardFilterViewModel(
    application: Application,
    private val configRepository: AppConfigRepository,
    private val recordRepository: MessageRecordRepository,
) : AndroidViewModel(application) {

    private val appLabelCache = ConcurrentHashMap<String, String>()
    private val _appForwardFilterUiState = MutableStateFlow(AppForwardFilterUiState())
    val appForwardFilterUiState: StateFlow<AppForwardFilterUiState> = _appForwardFilterUiState.asStateFlow()

    fun globalForwardRulesFlow(msgType: String): Flow<List<ForwardFilterRule>> {
        return configRepository.observeForwardFiltersByScope(
            msgType = msgType,
            scopeType = ForwardFilterConst.SCOPE_GLOBAL,
        )
    }

    fun appPackageForwardRulesFlow(packageName: String): Flow<List<ForwardFilterRule>> {
        return configRepository.observeForwardFiltersByScope(
            msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
            scopeType = ForwardFilterConst.SCOPE_PACKAGE,
            scopeKey = packageName.trim(),
        )
    }

    fun appChannelForwardRulesFlow(packageName: String): Flow<List<ForwardFilterRule>> {
        return configRepository.observeForwardFiltersByScopePrefix(
            msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
            scopeType = ForwardFilterConst.SCOPE_ANDROID_CHANNEL,
            scopeKeyPrefix = "${packageName.trim()}::%",
        )
    }

    fun appNotifyChannelHistoryFlow(packageName: String, limit: Int = 20): Flow<List<String>> {
        return recordRepository.observeRecentNotifyChannelIds(
            packageName = packageName.trim(),
            limit = limit,
        )
    }

    fun loadAppForwardFilterHeader(packageName: String) {
        val normalizedPackage = packageName.trim()
        if (normalizedPackage.isEmpty()) {
            _appForwardFilterUiState.value = AppForwardFilterUiState()
            return
        }
        val current = _appForwardFilterUiState.value
        if (current.packageName == normalizedPackage && current.appLabel.isNotBlank()) return

        _appForwardFilterUiState.value = AppForwardFilterUiState(
            packageName = normalizedPackage,
            appLabel = appLabelCache[normalizedPackage] ?: normalizedPackage,
            isResolvingLabel = true,
        )
        viewModelScope.launch {
            val resolved = resolveAppLabel(normalizedPackage)
            _appForwardFilterUiState.update { state ->
                if (state.packageName == normalizedPackage) {
                    state.copy(appLabel = resolved, isResolvingLabel = false)
                } else {
                    state
                }
            }
        }
    }

    fun saveForwardFilterRule(rule: ForwardFilterRule) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = rule.copy(
                updateTime = System.currentTimeMillis(),
                scopeKey = rule.scopeKey.trim(),
                pattern = rule.pattern.trim(),
            )
            if (normalized.id <= 0L) {
                configRepository.insertForwardFilterRule(normalized.copy(id = 0L))
            } else {
                configRepository.updateForwardFilterRule(normalized)
            }
        }
    }

    fun deleteForwardFilterRule(id: Long) {
        if (id <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.deleteForwardFilterRuleById(id)
        }
    }

    fun setForwardFilterRuleEnabled(id: Long, enabled: Boolean) {
        if (id <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.updateForwardFilterEnabled(
                id = id,
                enabled = if (enabled) 1 else 0,
                updateTime = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun resolveAppLabel(packageName: String): String = withContext(Dispatchers.IO) {
        appLabelCache[packageName] ?: runCatching {
            val packageManager = getApplication<Application>().packageManager
            val appInfo = packageManager.getApplicationInfoCompat(packageName)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
            ?.ifBlank { null }
            .let { normalizeAppForwardFilterLabel(packageName, it) }
            .also { resolved ->
                appLabelCache[packageName] = resolved
            }
    }
}

@Immutable
data class AppForwardFilterUiState(
    val packageName: String = "",
    val appLabel: String = "",
    val isResolvingLabel: Boolean = false,
)

internal fun normalizeAppForwardFilterLabel(packageName: String, resolvedLabel: String?): String {
    return resolvedLabel
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: packageName.trim()
}

private fun PackageManager.getApplicationInfoCompat(packageName: String) =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, 0)
    }
