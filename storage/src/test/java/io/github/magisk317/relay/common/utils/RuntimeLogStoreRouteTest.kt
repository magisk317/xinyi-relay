package io.github.magisk317.relay.common.utils

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
            RuntimeLogStore.ROUTE_NMS_HOOK,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.xp.hook.notification.NotificationManagerHook"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_SYSTEM_INPUT,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.xp.hook.system.SystemInputInjectorHook"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_PERMISSION_HOOK,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.xp.hook.permission.PermissionGranterHook"),
        )
        assertEquals(
            RuntimeLogStore.ROUTE_GOOGLE_MESSAGES,
            RuntimeLogStore.routeFromCallerClassName("io.github.magisk317.relay.xp.hook.google.GoogleMessagesHook"),
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
