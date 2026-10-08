package io.github.magisk317.relay.xp.hook.forward

import android.content.Intent
import io.github.magisk317.smscode.runtime.contract.sim.SmsRoutingMetadata
import io.github.magisk317.smscode.xposed.hook.telephony.SmsRoutingProbeResolver

/**
 * SIM routing lookup, delegated to the shared resolver in core.
 *
 * The prober itself used to live here, next to the forwarder, but nothing in it was
 * forward-specific. It is kept under the old name so the forward path is unchanged.
 */
internal typealias SmsForwardSimRouting = SmsRoutingMetadata

internal object SmsForwardSimRoutingResolver {

    fun readFromIntent(intent: Intent): SmsForwardSimRouting =
        SmsRoutingProbeResolver.readFromIntent(intent)

    fun ensureSimRoutingExtras(
        intent: Intent,
        handler: Any?,
        args: Array<Any?>?,
    ): SmsForwardSimRouting? = SmsRoutingProbeResolver.ensureSimRoutingExtras(
        intent = intent,
        handler = handler,
        args = args,
    )

    fun resolve(
        handler: Any?,
        args: Array<Any?>?,
    ): SmsForwardSimRouting? = SmsRoutingProbeResolver.resolve(handler = handler, args = args)

    fun debugSnapshot(
        handler: Any?,
        args: Array<Any?>?,
    ): String = SmsRoutingProbeResolver.debugSnapshot(handler = handler, args = args)
}
