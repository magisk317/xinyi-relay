package io.github.magisk317.relay.ui.app

import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
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
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object XposedServiceBridge {
    @Volatile
    private var currentService: XposedService? = null

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
                    currentService?.let(::publishScope)
                }

                override fun requestMissing(
                    packages: List<String>,
                    onResult: (ScopeRequestOutcome) -> Unit,
                ) {
                    val service = currentService
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
        runCatching<Unit> {
            XposedServiceHelper.registerListener(
                object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(service: XposedService) {
                        currentService = service
                        publishScope(service)
                        val mode = XposedServiceRuntimeCoordinator.handleServiceBound(
                            application = application,
                            applicationScope = applicationScope,
                            remotePrefsProvider = { service.getRemotePreferences(RelayPrefConst.REMOTE_PREFS_GROUP) },
                            frameworkName = service.frameworkName,
                            frameworkVersion = service.frameworkVersion,
                        )
                        InfrastructureInitializer.markEnvironmentSettled()
                        PhoneProcessRestartCoordinator.requestAfterInstallOrUpdate(application, applicationScope)
                    }

                    override fun onServiceDied(service: XposedService) {
                        currentService = null
                        ProviderCallerPolicy.updateLiveScope(emptyList())
                        XposedScopeAccessHolder.update(XposedScopeState())
                        val mode = XposedServiceRuntimeCoordinator.handleServiceDied(application)
                        InfrastructureInitializer.markEnvironmentSettled()
                    }
                },
            )
        }.onFailure(XposedServiceRuntimeCoordinator::logRegistrationFailure)
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
        XposedScopeAccessHolder.update(
            XposedScopeState(
                connected = true,
                frameworkName = service.frameworkName,
                frameworkVersion = service.frameworkVersion,
                packages = packages,
                refreshedAtMs = System.currentTimeMillis(),
            ),
        )
    }
}
