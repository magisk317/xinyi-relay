package io.github.magisk317.relay.entitlement

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator as PrivateMobileEntitlementCoordinator
import io.github.magisk317.uikit.state.TimedValueCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias MobileEntitlementStatus = com.magisk317.mobile.entitlement.MobileEntitlementStatus
typealias MobileEntitlementClaims = com.magisk317.mobile.entitlement.MobileEntitlementClaims
typealias MobileEntitlementEvaluation = com.magisk317.mobile.entitlement.MobileEntitlementEvaluation
typealias MobileEntitlementChallenge = com.magisk317.mobile.entitlement.MobileEntitlementChallenge
typealias MobileEntitlementGoogleChallenge = com.magisk317.mobile.entitlement.MobileEntitlementGoogleChallenge
typealias MobileEntitlementActivationStatus = com.magisk317.mobile.entitlement.MobileEntitlementActivationStatus
typealias MobileEntitlementActivationState = com.magisk317.mobile.entitlement.MobileEntitlementActivationState

private const val MOBILE_ENTITLEMENT_CACHE_TTL_MS = 5 * 60 * 1_000L

object MobileEntitlementCoordinator {
    private val refreshMutex = Mutex()
    private val evaluationCache = TimedValueCache<MobileEntitlementEvaluation>(
        ttlMs = MOBILE_ENTITLEMENT_CACHE_TTL_MS,
    )

    @Volatile
    private var initialized = false

    fun initialize(context: Context, scope: CoroutineScope) {
        if (!isMainProcess(context)) return
        synchronized(this) {
            if (initialized) return
            initialized = true
        }
        scope.launch(Dispatchers.IO) {
            runCatching { refresh(context) }
        }
    }

    fun readCachedEvaluation(): MobileEntitlementEvaluation? = evaluationCache.peek()

    fun readPendingChallenge(context: Context): String? =
        PrivateMobileEntitlementCoordinator.readPendingChallenge(context)

    fun clearPendingChallenge(context: Context) =
        PrivateMobileEntitlementCoordinator.clearPendingChallenge(context)

    suspend fun refresh(
        context: Context,
        force: Boolean = false,
    ): MobileEntitlementEvaluation {
        if (!force) evaluationCache.freshOrNull()?.let { return it }

        return refreshMutex.withLock {
            if (!force) evaluationCache.freshOrNull()?.let { return@withLock it }
            PrivateMobileEntitlementCoordinator.refresh(context).also(::updateCache)
        }
    }

    suspend fun createTelegramChallenge(context: Context): MobileEntitlementChallenge =
        PrivateMobileEntitlementCoordinator.createTelegramChallenge(context)

    suspend fun createGoogleChallenge(context: Context): MobileEntitlementGoogleChallenge =
        PrivateMobileEntitlementCoordinator.createGoogleChallenge(context)

    suspend fun pollTelegramChallenge(
        context: Context,
        challengeId: String,
    ): MobileEntitlementActivationState =
        PrivateMobileEntitlementCoordinator.pollTelegramChallenge(context, challengeId).also { state ->
            state.evaluation?.let(::updateCache)
        }

    suspend fun activateByToken(
        context: Context,
        token: String,
    ): MobileEntitlementEvaluation =
        PrivateMobileEntitlementCoordinator.activateByToken(context, token).also(::updateCache)

    private fun updateCache(evaluation: MobileEntitlementEvaluation) {
        evaluationCache.put(evaluation)
    }

    private fun isMainProcess(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            manager?.runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }
                ?.processName
        }
        return processName == appContext.packageName
    }
}
