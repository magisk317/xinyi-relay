package io.github.magisk317.relay.feature.mode

import android.content.Context
import android.os.SystemClock
import io.github.magisk317.xposed.runtime.state.FrameworkRead
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore
import io.github.magisk317.xposed.runtime.state.XposedActivation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Publishes current hook activity from the fresh framework read and this boot's heartbeat. */
object XposedRuntimeState {
    private val _xposedActive = MutableStateFlow(false)
    val xposedActive: StateFlow<Boolean> = _xposedActive.asStateFlow()

    fun refresh(context: Context, frameworkRead: FrameworkRead): Boolean {
        val now = System.currentTimeMillis()
        val active = XposedActivation.isActive(
            frameworkRead = frameworkRead,
            lastHeartbeatAtMs = RuntimeStateStore.snapshot(context).lastHookAtMs,
            nowMs = now,
            bootStartMs = now - SystemClock.elapsedRealtime(),
        )
        _xposedActive.value = active
        return active
    }
}
