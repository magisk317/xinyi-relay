package io.github.magisk317.relay.ui.app

import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.magisk317.relay.android.platform.ipc.ProviderCallerPolicy
import io.github.magisk317.relay.app.InfrastructureInitializer
import io.github.magisk317.relay.app.XposedServiceRuntimeCoordinator
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.relay.domain.diagnostics.ScopeRequestOutcome
import io.github.magisk317.relay.domain.diagnostics.XposedScopeAccess
import io.github.magisk317.relay.domain.diagnostics.XposedScopeAccessHolder
import io.github.magisk317.relay.domain.diagnostics.XposedScopeState
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.runtime.state.FrameworkRead
import org.koin.core.context.GlobalContext
import io.github.magisk317.xposed.service.FrameworkHookTargets
import io.github.magisk317.xposed.service.ServiceState
import io.github.magisk317.xposed.service.XposedServiceBridge as KitServiceBridge
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The kit owns the libxposed listener and its bind/retract state; this object only maps each
 * transition onto the effects the app has always performed on them.
 */
internal object XposedServiceBridge {
    private lateinit var application: SmsCodeApplication
    private lateinit var applicationScope: CoroutineScope

    private val mainExecutor = Executor { runnable ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            Handler(Looper.getMainLooper()).post(runnable)
        }
    }

    fun initialize(application: SmsCodeApplication, applicationScope: CoroutineScope) {
        this.application = application
        this.applicationScope = applicationScope
        XposedScopeAccessHolder.install(
            object : XposedScopeAccess {
                override fun refresh() {
                    KitServiceBridge.currentService?.let(::publishScope)
                }

                override fun requestMissing(
                    packages: List<String>,
                    onResult: (ScopeRequestOutcome) -> Unit,
                ) {
                    val service = KitServiceBridge.currentService
                    if (service == null) {
                        onResult(ScopeRequestOutcome.Unavailable("框架服务未连接"))
                        return
                    }
                    val granted = runCatching { service.scope }.getOrElse { error ->
                        onResult(
                            ScopeRequestOutcome.Failed(
                                error.message ?: error.javaClass.simpleName,
                            ),
                        )
                        return
                    }
                    val missing = packages.filterNot { it in granted }.distinct()
                    if (missing.isEmpty()) {
                        publishScope(service)
                        onResult(ScopeRequestOutcome.Approved(emptyList()))
                        return
                    }
                    runCatching {
                        service.requestScope(
                            missing,
                            object : XposedService.OnScopeEventListener {
                                override fun onScopeRequestApproved(approved: List<String>) {
                                    mainExecutor.execute {
                                        publishScope(service)
                                        onResult(ScopeRequestOutcome.Approved(approved))
                                    }
                                }

                                override fun onScopeRequestFailed(message: String) {
                                    mainExecutor.execute {
                                        onResult(ScopeRequestOutcome.Rejected(message))
                                    }
                                }
                            },
                        )
                    }.onFailure { error ->
                        onResult(
                            ScopeRequestOutcome.Failed(
                                error.message ?: error.javaClass.simpleName,
                            ),
                        )
                    }
                }
            },
        )
        val registered = KitServiceBridge.register(
            onError = { operation, error ->
                XLog.w(
                    message = "Xposed service bridge failed during %s: %s",
                    operation,
                    error.message ?: error.javaClass.simpleName,
                )
                XposedServiceRuntimeCoordinator.logRegistrationFailure(error)
            },
        )
        if (registered) {
            XLog.i(message = "XposedServiceHelper.registerListener() succeeded")
        }
        applicationScope.launch {
            KitServiceBridge.state.collect { state ->
                when (state) {
                    is ServiceState.Bound -> onBound(application, applicationScope, state.service)
                    ServiceState.Retracted -> onRetracted(application)
                    ServiceState.Waiting -> Unit
                }
            }
        }
    }

    private fun onBound(
        application: SmsCodeApplication,
        applicationScope: CoroutineScope,
        service: XposedService,
    ) {
        publishScope(service)
        // The framework is the only side that knows which processes carry the module, so this keeps
        // the activation status independent from the hook having to reach the app through a
        // content provider the system may refuse to start.
        runCatching { FrameworkHookTargets.record(application, service) }
            .onFailure { error -> XLog.w(message = "Failed to record framework targets: %s", error) }
        val signals = GlobalContext.get().get<XposedRuntimeSignals>()
        val identity = signals.frameworkIdentity()
        XposedServiceRuntimeCoordinator.handleServiceBound(
            application = application,
            applicationScope = applicationScope,
            remotePrefsProvider = { service.getRemotePreferences(RelayPrefConst.REMOTE_PREFS_GROUP) },
            frameworkName = identity.name,
            frameworkVersion = identity.version,
            frameworkRead = signals.currentFrameworkRead(),
        )
        InfrastructureInitializer.markEnvironmentSettled()
        PhoneProcessRestartCoordinator.requestAfterInstallOrUpdate(application, applicationScope)
    }

    private fun onRetracted(application: SmsCodeApplication) {
        ProviderCallerPolicy.updateLiveScope(emptyList())
        XposedScopeAccessHolder.update(XposedScopeState())
        XposedServiceRuntimeCoordinator.handleServiceDied(application, FrameworkRead.Unavailable)
        InfrastructureInitializer.markEnvironmentSettled()
    }

    private fun publishScope(service: XposedService) {
        val packages = runCatching { service.scope }.getOrDefault(emptyList())
        ProviderCallerPolicy.updateLiveScope(packages)
        applicationScope.launch {
            runCatching {
                AppPreferencesDataStore.setString(
                    application,
                    RelayPrefConst.KEY_XPOSED_SCOPE_CACHE,
                    packages.joinToString(","),
                )
            }.onFailure { error ->
                XLog.w(message = "failed to cache the live Xposed scope: ${error.message}", error)
            }
        }
        val identity = GlobalContext.get().get<XposedRuntimeSignals>().frameworkIdentity()
        XposedScopeAccessHolder.update(
            XposedScopeState(
                connected = true,
                frameworkName = identity.name.orEmpty(),
                frameworkVersion = identity.version.orEmpty(),
                packages = packages,
                refreshedAtMs = System.currentTimeMillis(),
            ),
        )
    }
}
