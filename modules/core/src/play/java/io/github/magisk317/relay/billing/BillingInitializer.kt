package io.github.magisk317.relay.billing

import android.app.Application
import io.github.magisk317.relay.app.AppInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BillingInitializer(
    private val billingManager: BillingManager,
) : AppInitializer {

    override fun init(application: Application) {
        billingManager.startConnection {
            // Connected to Google Play Billing
            CoroutineScope(Dispatchers.IO).launch {
                billingManager.querySubscriptions()
                billingManager.queryDonations()
            }
        }
    }
}
