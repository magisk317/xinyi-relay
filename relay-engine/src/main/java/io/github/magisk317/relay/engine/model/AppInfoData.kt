package io.github.magisk317.relay.engine.model

interface AppInfoData {
    val packageName: String
    val label: String?
    val blocked: Boolean
    val forwarding: Boolean
    val forwardingConfigured: Boolean
    val notifyTemplate: String
}