package io.github.magisk317.relay.platform.ipc

/**
 * Legacy FQN kept so senders installed before an app update keep resolving
 * this component; the ingest logic lives in
 * [io.github.magisk317.forward.runtime.ingress.CustomMessageReceiver].
 */
class CustomMessageReceiver : io.github.magisk317.forward.runtime.ingress.CustomMessageReceiver()
