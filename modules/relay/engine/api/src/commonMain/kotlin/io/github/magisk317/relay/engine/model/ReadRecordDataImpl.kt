package io.github.magisk317.relay.engine.model

/**
 * Domain implementation of [ReadRecordData].
 *
 * The Room entity (`SmsMsg`) lives in the shared `:smscode-core:db` module, which must not
 * depend on `:relay:engine:api` (that would create a dependency cycle). This class bridges
 * the two; see `ConfigMapper.toRecordData` for the entity -> domain conversion.
 */
data class ReadRecordDataImpl(
    override val id: Long,
    override val sender: String?,
    override val body: String?,
    override val date: Long,
    override val processedTime: Long,
    override val company: String?,
    override val smsCode: String?,
    override val packageName: String?,
    override val notifyChannelId: String,
    override val simSlot: Int,
    override val subId: Int,
    override val contactName: String,
    override val phoneArea: String,
    override val forwardStatus: Int,
    override val forwardTarget: String?,
    override val forwardMessage: String?,
    override val forwardTime: Long,
    override val msgType: Int,
    override val callType: Int,
    override val sessionKey: String,
) : ReadRecordData
