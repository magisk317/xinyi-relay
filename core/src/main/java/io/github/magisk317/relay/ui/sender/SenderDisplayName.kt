package io.github.magisk317.relay.ui.sender

import android.content.Context
import io.github.magisk317.relay.domain.model.Sender

fun Sender.displayName(context: Context): String = name.ifBlank { getSenderTypeName(context, type) }
