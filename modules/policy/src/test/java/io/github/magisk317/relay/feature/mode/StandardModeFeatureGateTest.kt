package io.github.magisk317.relay.feature.mode

import io.github.magisk317.relay.feature.mode.StandardModeFeatureGate.Feature.BLOCK_SMS
import io.github.magisk317.relay.feature.mode.StandardModeFeatureGate.Feature.KEEPALIVE_OOM_ADJ
import io.github.magisk317.relay.feature.mode.StandardModeFeatureGate.Feature.ROOT_DB_CATCHUP
import io.github.magisk317.relay.feature.mode.StandardModeFeatureGate.Feature.SMS_HOOK_INTERCEPT
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StandardModeFeatureGateTest : FunSpec({

    test("Xposed-only features are available only in enhanced mode") {
        listOf(SMS_HOOK_INTERCEPT, BLOCK_SMS, KEEPALIVE_OOM_ADJ).forEach { feature ->
            StandardModeFeatureGate.isAvailable(feature, WorkMode.Enhanced) shouldBe true
            StandardModeFeatureGate.isAvailable(feature, WorkMode.Standard) shouldBe false
            StandardModeFeatureGate.disabledReason(feature, WorkMode.Standard) shouldBe "requires_xposed"
        }
    }

    test("Root-only features depend on root access independently from Xposed mode") {
        StandardModeFeatureGate.isAvailable(ROOT_DB_CATCHUP, WorkMode.Enhanced, hasRootAccess = false) shouldBe false
        StandardModeFeatureGate.isAvailable(ROOT_DB_CATCHUP, WorkMode.Enhanced, hasRootAccess = true) shouldBe true
        StandardModeFeatureGate.isAvailable(ROOT_DB_CATCHUP, WorkMode.Standard, hasRootAccess = false) shouldBe false
        StandardModeFeatureGate.isAvailable(ROOT_DB_CATCHUP, WorkMode.Standard, hasRootAccess = true) shouldBe true
        StandardModeFeatureGate.disabledReason(ROOT_DB_CATCHUP, WorkMode.Standard, hasRootAccess = false) shouldBe "requires_root"
    }

    test("Inactive mode disables all gated features") {
        StandardModeFeatureGate.isAvailable(SMS_HOOK_INTERCEPT, WorkMode.Inactive, hasRootAccess = true) shouldBe false
        StandardModeFeatureGate.isAvailable(ROOT_DB_CATCHUP, WorkMode.Inactive, hasRootAccess = true) shouldBe false
        StandardModeFeatureGate.disabledReason(ROOT_DB_CATCHUP, WorkMode.Inactive, hasRootAccess = true) shouldBe "inactive"
    }
})
