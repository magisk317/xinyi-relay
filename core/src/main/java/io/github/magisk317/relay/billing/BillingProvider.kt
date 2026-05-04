package io.github.magisk317.relay.billing

import android.app.Activity

/**
 * Minimal interface for billing operations.
 * Real implementation lives in play flavor; github gets a no-op stub.
 */
interface BillingProvider {
    fun isAvailable(): Boolean
    fun isSubscriptionActive(): Boolean
    fun getActiveProductId(): String?
    fun refreshSubscriptionStatus()
    fun launchSubscription(activity: Activity, productId: String)
    fun launchDonation(activity: Activity, productId: String)
}

class NoOpBillingProvider : BillingProvider {
    override fun isAvailable(): Boolean = false
    override fun isSubscriptionActive(): Boolean = false
    override fun getActiveProductId(): String? = null
    override fun refreshSubscriptionStatus() {}
    override fun launchSubscription(activity: Activity, productId: String) {}
    override fun launchDonation(activity: Activity, productId: String) {}
}
