package com.github.magisk317.smscode.ui.common

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun rememberMinDurationLoading(
    actualLoading: Boolean,
    minDurationMillis: Long = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
): Boolean {
    var visibleLoading by remember { mutableStateOf(actualLoading) }
    var loadingStartAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(actualLoading) {
        if (actualLoading) {
            if (loadingStartAt == 0L) {
                loadingStartAt = SystemClock.elapsedRealtime()
            }
            visibleLoading = true
        } else {
            if (loadingStartAt == 0L) {
                visibleLoading = false
                return@LaunchedEffect
            }
            val elapsed = SystemClock.elapsedRealtime() - loadingStartAt
            val remaining = (minDurationMillis - elapsed).coerceAtLeast(0L)
            if (remaining > 0) {
                delay(remaining)
            }
            visibleLoading = false
            loadingStartAt = 0L
        }
    }

    return visibleLoading
}
