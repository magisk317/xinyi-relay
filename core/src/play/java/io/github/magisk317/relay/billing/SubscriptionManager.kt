package io.github.magisk317.relay.billing

import android.content.Context
import com.android.billingclient.api.Purchase
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class SubscriptionManager(
    context: Context,
    private val billingManager: BillingManager,
    private val preferenceDataSource: PreferenceDataSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class SubscriptionStatus {
        FREE,
        ACTIVE_SUB,
        EXPIRED,
    }

    private val _status = MutableStateFlow(SubscriptionStatus.FREE)
    val status: StateFlow<SubscriptionStatus> = _status.asStateFlow()

    private val _activeProductId = MutableStateFlow<String?>(null)
    val activeProductId: StateFlow<String?> = _activeProductId.asStateFlow()

    init {
        scope.launch {
            refreshSubscriptionStatus()
        }
    }

    suspend fun refreshSubscriptionStatus() {
        try {
            val purchases = billingManager.queryActivePurchases()
            val activeSub = purchases.firstOrNull { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (activeSub != null) {
                _status.value = SubscriptionStatus.ACTIVE_SUB
                _activeProductId.value = activeSub.products.firstOrNull()
                Timber.d("Active subscription: ${_activeProductId.value}")
            } else {
                _status.value = SubscriptionStatus.FREE
                _activeProductId.value = null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh subscription status")
        }
    }

    fun isActive(): Boolean = _status.value == SubscriptionStatus.ACTIVE_SUB

    fun getStatus(): SubscriptionStatus = _status.value

    fun getActiveProductId(): String? = _activeProductId.value
}
