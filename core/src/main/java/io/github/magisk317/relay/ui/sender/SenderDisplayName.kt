package io.github.magisk317.relay.ui.sender

import io.github.magisk317.relay.model.Sender

fun Sender.displayName(): String = name.ifBlank { getSenderTypeName(type) }
