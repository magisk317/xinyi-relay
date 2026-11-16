package io.github.magisk317.relay.matrix.e2ee

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Atomic before/after pair for one committed state transition. */
internal data class StateTransition<T>(val before: T, val after: T)

/**
 * Applies [transform] atomically and reports the transition that actually committed.
 *
 * `MutableStateFlow.update` retries under contention; the captured pair comes from the
 * invocation whose result won the CAS, so concurrent reducers cannot overwrite each other.
 */
internal inline fun <T> MutableStateFlow<T>.updateWithTransition(
    transform: (T) -> T,
): StateTransition<T> {
    var transition: StateTransition<T>? = null
    update { current ->
        val next = transform(current)
        transition = StateTransition(current, next)
        next
    }
    return checkNotNull(transition)
}
