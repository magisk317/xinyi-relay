package io.github.magisk317.relay.platform.ipc

/**
 * Legacy FQN kept so hooks installed before an app update keep resolving this
 * component; the ingest logic lives in
 * [io.github.magisk317.forward.runtime.ingress.ForwardReceiver].
 */
class ForwardReceiver : io.github.magisk317.forward.runtime.ingress.ForwardReceiver()
