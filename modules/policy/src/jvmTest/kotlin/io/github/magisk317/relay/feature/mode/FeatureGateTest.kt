package io.github.magisk317.relay.feature.mode

import io.github.magisk317.relay.feature.mode.FeatureGate.Feature.BLOCK_SMS
import io.github.magisk317.relay.feature.mode.FeatureGate.Feature.KEEPALIVE_OOM_ADJ
import io.github.magisk317.relay.feature.mode.FeatureGate.Feature.ROOT_DB_CATCHUP
import io.github.magisk317.relay.feature.mode.FeatureGate.Feature.SMS_HOOK_INTERCEPT
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FeatureGateTest : FunSpec({

    test("Hook-backed features need a live Xposed runtime") {
        listOf(SMS_HOOK_INTERCEPT, BLOCK_SMS, KEEPALIVE_OOM_ADJ).forEach { feature ->
            FeatureGate.isAvailable(feature, xposedActive = true) shouldBe true
            FeatureGate.isAvailable(feature, xposedActive = false) shouldBe false
        }
    }

    test("Root-only features need root access on top of the Xposed runtime") {
        FeatureGate.isAvailable(ROOT_DB_CATCHUP, xposedActive = true, hasRootAccess = false) shouldBe false
        FeatureGate.isAvailable(ROOT_DB_CATCHUP, xposedActive = true, hasRootAccess = true) shouldBe true
    }

    test("An inactive Xposed runtime disables every gated feature") {
        FeatureGate.isAvailable(SMS_HOOK_INTERCEPT, xposedActive = false, hasRootAccess = true) shouldBe false
        FeatureGate.isAvailable(ROOT_DB_CATCHUP, xposedActive = false, hasRootAccess = true) shouldBe false
    }

    test("allAvailable requires every feature to be available") {
        val keepAlive = arrayOf(FeatureGate.Feature.KEEPALIVE_ANTI_KILL, KEEPALIVE_OOM_ADJ)
        FeatureGate.allAvailable(xposedActive = true, *keepAlive) shouldBe true
        FeatureGate.allAvailable(xposedActive = false, *keepAlive) shouldBe false
    }
})
