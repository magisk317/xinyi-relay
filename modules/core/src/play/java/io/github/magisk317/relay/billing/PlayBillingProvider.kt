package io.github.magisk317.relay.billing

import android.app.Activity

class PlayBillingProvider(
    private val billingManager: BillingManager,
    private val subscriptionManager: SubscriptionManager,
) : BillingProvider {

    override fun isAvailable(): Boolean = billingManager.isReady()

    override fun isSubscriptionActive(): Boolean = subscriptionManager.isActive()

    override fun getActiveProductId(): String? = subscriptionManager.getActiveProductId()

    override fun refreshSubscriptionStatus() {
        subscriptionManager.let {
            // refreshSubscriptionStatus is a suspend function, but BillingProvider interface is sync
            // The status is automatically refreshed when purchases are queried
        }
    }

    override fun launchSubscription(activity: Activity, productId: String) {
        val details = billingManager.subscriptionDetails.value.find { detail ->
            detail.productId == productId
        } ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        billingManager.launchPurchaseFlow(activity, details, offerToken)
    }

    override fun launchDonation(activity: Activity, productId: String) {
        val details = billingManager.donationDetails.value.find { detail ->
            detail.productId == productId
        } ?: return
        billingManager.launchDonationFlow(activity, details)
    }
}
