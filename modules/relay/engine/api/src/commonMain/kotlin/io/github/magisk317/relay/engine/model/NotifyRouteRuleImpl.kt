package io.github.magisk317.relay.engine.model

/**
 * Domain implementation of [NotifyRouteRuleData].
 *
 * The Room entity lives in the shared `:smscode-core:db` module, which must not depend on
 * `:relay:engine:api` (that would create a dependency cycle). This class bridges the two;
 * see `ConfigMapper` for the entity <-> domain conversions.
 */
data class NotifyRouteRuleImpl(
    override val id: Long = 0L,
    override val scope: Int,
    override val packageName: String,
    override val senderId: Long,
    override val updateTime: Long = 0L,
) : NotifyRouteRuleData
