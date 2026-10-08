package io.github.magisk317.relay.engine.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Domain implementation of [SmsCodeRuleData].
 *
 * The Room entity lives in the shared `:smscode-core:db` module, which must not depend on
 * `:relay:engine:api` (that would create a dependency cycle). This class bridges the two;
 * see `ConfigMapper` for the entity <-> domain conversions.
 */
@Parcelize
@Serializable
data class SmsCodeRuleImpl(
    override val id: Long = 0L,
    override val company: String? = null,
    override val codeKeyword: String = "",
    override val codeRegex: String = "",
) : Parcelable, SmsCodeRuleData
