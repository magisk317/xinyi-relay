package io.github.magisk317.relay.engine.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Domain implementation of [AppInfoData].
 *
 * The Room entity lives in the shared `:smscode-core:db` module, which must not depend on
 * `:relay:engine:api` (that would create a dependency cycle). This class bridges the two;
 * see `ConfigMapper` for the entity <-> domain conversions.
 */
@Parcelize
@Serializable
data class AppInfoImpl(
    override val packageName: String = "",
    override val label: String? = null,
    override val blocked: Boolean = false,
    override val forwarding: Boolean = false,
    override val forwardingConfigured: Boolean = false,
    override val notifyTemplate: String = "",
) : Parcelable, AppInfoData
