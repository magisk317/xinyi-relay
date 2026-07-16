package io.github.magisk317.relay.feature.mode

import android.Manifest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly

class StandardModePermissionsTest : FunSpec({
    test("distribution only requests declared standard capabilities") {
        StandardModePermissions.requiredPermissionsFromDeclared(
            setOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
        ) shouldContainExactly listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )
    }

    test("distribution with no telephony declarations has no telephony permission prompt") {
        StandardModePermissions.requiredPermissionsFromDeclared(emptySet()) shouldContainExactly emptyList()
    }

    test("package inspection failure falls back to the conservative full list") {
        StandardModePermissions.requiredPermissionsFromDeclared(null) shouldContainExactly
            StandardModePermissions.REQUIRED_PERMISSIONS.toList()
    }
})
