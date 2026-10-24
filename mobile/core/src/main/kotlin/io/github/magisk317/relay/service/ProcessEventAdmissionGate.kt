package io.github.magisk317.relay.service

import java.util.concurrent.atomic.AtomicBoolean

internal class ProcessEventAdmissionGate {
    private val active = AtomicBoolean(false)

    fun tryEnter(): Boolean = active.compareAndSet(false, true)

    fun leave() {
        active.set(false)
    }
}
