package io.github.magisk317.relay.ui.billing

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.magisk317.relay.billing.BillingProvider
import io.github.magisk317.relay.mobileui.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DonateViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val billingProvider: BillingProvider by inject()

    private val _events = MutableSharedFlow<DonateEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DonateEvent> = _events.asSharedFlow()

    fun isAvailable(): Boolean = BuildConfig.HAS_BILLING && billingProvider.isAvailable()

    fun isSubscriptionActive(): Boolean = billingProvider.isSubscriptionActive()

    fun getActiveProductId(): String? = billingProvider.getActiveProductId()

    fun launchSubscription(activity: Activity, productId: String) {
        billingProvider.launchSubscription(activity, productId)
    }

    fun launchDonation(activity: Activity, productId: String) {
        billingProvider.launchDonation(activity, productId)
    }

    fun refreshStatus() {
        billingProvider.refreshSubscriptionStatus()
    }

    sealed class DonateEvent {
        data object PurchaseSuccess : DonateEvent()
        data class PurchaseFailed(val message: String) : DonateEvent()
    }
}
