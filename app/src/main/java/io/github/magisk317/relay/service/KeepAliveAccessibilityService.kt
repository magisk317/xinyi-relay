package io.github.magisk317.relay.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * Minimal accessibility service that exists solely as a system-managed anchor.
 * Android automatically restarts enabled accessibility services when they die,
 * which keeps the app process alive.
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        XLog.w("KeepAliveAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        XLog.w("KeepAliveAccessibilityService destroyed")
        super.onDestroy()
    }
}
