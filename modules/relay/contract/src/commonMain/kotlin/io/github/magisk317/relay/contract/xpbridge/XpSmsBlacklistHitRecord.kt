package io.github.magisk317.relay.contract.xpbridge

data class XpSmsBlacklistHitRecord(
    val eventId: String,
    val source: String,
    val sender: String? = null,
    val body: String? = null,
    val smsDate: Long = 0L,
    val matchType: String? = null,
    val pattern: String? = null,
    val actionDelete: Boolean = false,
    val actionBlock: Boolean = false,
    val blockReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
