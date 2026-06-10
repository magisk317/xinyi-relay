package io.github.magisk317.relay.android.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuntimeLogStoreRouteTest {

    @Test
    fun routeFromCallerClassName_mapsKnownHookPackages() {
        assertEquals(
            RuntimeLogStore.ROUTE_SMS_HOOK,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.xp.hook.code.SmsHandlerHook"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_FORWARD,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.xp.hook.forward.SmsForwardHook"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_SMS_HOOK,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.xp.hook.telephony.SmsProviderHook"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_SMS_HOOK,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.xp.LibXposedEntry"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_NMS_HOOK,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.smscode.xposed.hook.notification.NotificationManagerHook"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_SYSTEM_INPUT,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_PERMISSION_HOOK,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.smscode.xposed.hook.permission.PermissionGranterHook"),
        )
    }

    @Test
    fun routeFromCallerClassName_mapsRootRecoveryAndFallback() {
        assertEquals(
            RuntimeLogStore.ROUTE_ROOT_DB,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.domain.recovery.RootDbCatchupEngine"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_APP,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.some.OtherClass"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_APP,
            RuntimeLogStore.routeFromCallerClassName(null),
        )
    }
}
