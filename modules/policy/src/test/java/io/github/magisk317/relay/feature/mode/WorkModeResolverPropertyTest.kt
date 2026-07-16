package io.github.magisk317.relay.feature.mode

import android.content.Context
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject

/**
 * Feature: standard-mode-fallback, Property 1: Mode Resolution Decision Table
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
 *
 * For either Xposed activation state, permissions do not select the app-wide mode:
 * the WorkModeResolver SHALL produce:
 * - Enhanced when isModuleActivated = true
 * - Standard when isModuleActivated = false
 *
 * Individual Standard capabilities enforce their own permissions.
 */
class WorkModeResolverPropertyTest : FunSpec({

    val context = mockk<Context>(relaxed = true)

    beforeSpec {
        mockkObject(ActivationDiagnosticsStore)
    }

    afterSpec {
        unmockkObject(ActivationDiagnosticsStore)
    }

    test("Property 1: mode resolution matches decision table for all inputs").config(
        invocations = 100,
    ) {
        checkAll(1, Arb.boolean()) { xposedActive ->
            every { ActivationDiagnosticsStore.isModuleActivated(context) } returns xposedActive

            val resolved = WorkModeResolver.resolve(context)

            val expected = if (xposedActive) WorkMode.Enhanced else WorkMode.Standard

            resolved shouldBe expected
            WorkModeResolver.mode.value shouldBe expected
        }
    }

    test("runtime activation transitions Standard to Enhanced and back immediately") {
        every { ActivationDiagnosticsStore.isModuleActivated(context) } returnsMany
            listOf(false, true, false)

        WorkModeResolver.resolve(context) shouldBe WorkMode.Standard
        WorkModeResolver.resolve(context) shouldBe WorkMode.Enhanced
        WorkModeResolver.resolve(context) shouldBe WorkMode.Standard
    }
})
