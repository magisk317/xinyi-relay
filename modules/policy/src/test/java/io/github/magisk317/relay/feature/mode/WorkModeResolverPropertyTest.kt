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
 * For any combination of (isModuleActivated: Boolean, allPermissionsGranted: Boolean),
 * the WorkModeResolver SHALL produce:
 * - Enhanced when isModuleActivated = true (regardless of permissions)
 * - Standard when isModuleActivated = false AND allPermissionsGranted = true
 * - Inactive when isModuleActivated = false AND allPermissionsGranted = false
 */
class WorkModeResolverPropertyTest : FunSpec({

    val context = mockk<Context>(relaxed = true)

    beforeSpec {
        mockkObject(ActivationDiagnosticsStore)
        mockkObject(StandardModePermissions)
    }

    afterSpec {
        unmockkObject(ActivationDiagnosticsStore)
        unmockkObject(StandardModePermissions)
    }

    test("Property 1: mode resolution matches decision table for all inputs").config(
        invocations = 100,
    ) {
        checkAll(1, Arb.boolean(), Arb.boolean()) { xposedActive, permissionsGranted ->
            every { ActivationDiagnosticsStore.isModuleActivated(context) } returns xposedActive
            every { StandardModePermissions.allGranted(context) } returns permissionsGranted

            WorkModeResolver.resolve(context)

            val expected = when {
                xposedActive -> WorkMode.Enhanced
                permissionsGranted -> WorkMode.Standard
                else -> WorkMode.Inactive
            }

            WorkModeResolver.mode.value shouldBe expected
        }
    }
})
