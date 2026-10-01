package io.github.magisk317.relay.engine.model

interface ReadSmsBlacklistHitData {
    val id: Long
    val eventId: String
    val source: String
    val sender: String?
    val body: String?
    val smsDate: Long
    val matchType: String?
    val pattern: String?
    val actionDelete: Boolean
    val actionBlock: Boolean
    val blockReason: String?
    val createdAt: Long
}
