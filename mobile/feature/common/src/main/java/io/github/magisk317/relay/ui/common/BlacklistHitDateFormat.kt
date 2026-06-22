package io.github.magisk317.relay.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun rememberBlacklistHitDateFormat(): SimpleDateFormat {
    return remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
}
