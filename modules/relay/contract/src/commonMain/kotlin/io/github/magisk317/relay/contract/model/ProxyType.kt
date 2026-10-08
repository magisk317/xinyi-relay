package io.github.magisk317.relay.contract.model

/**
 * Proxy kind carried by sender settings.
 *
 * Replaces `java.net.Proxy.Type`, which is a platform class that commonMain
 * cannot name: under KMP separate compilation common code is resolved against
 * metadata KLIBs only. The entries keep the Java enum names, so the serialized
 * JSON form (`"DIRECT"`, `"HTTP"`, `"SOCKS"`) is unchanged.
 */
enum class ProxyType {
    DIRECT,
    HTTP,
    SOCKS,
}
