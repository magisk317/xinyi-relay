package io.github.magisk317.relay.ui.sender

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal const val SENDER_UNDO_SNACKBAR_DURATION_MS = 5_000L

private const val UNDO_COUNTDOWN_TICK_MS = 50L

@Composable
internal fun UndoCountdownSnackbar(
    data: SnackbarData,
    totalDurationMs: Long,
) {
    val startTimeMs = remember(data) { SystemClock.elapsedRealtime() }
    var nowMs by remember(data) { mutableLongStateOf(startTimeMs) }

    LaunchedEffect(data) {
        while (isActive) {
            nowMs = SystemClock.elapsedRealtime()
            delay(UNDO_COUNTDOWN_TICK_MS)
        }
    }

    val elapsedMs = (nowMs - startTimeMs).coerceIn(0L, totalDurationMs)
    val remainingMs = (totalDurationMs - elapsedMs).coerceAtLeast(0L)
    val progress = if (totalDurationMs <= 0L) {
        0f
    } else {
        (remainingMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    }
    val remainingSeconds = ceil(remainingMs / 1000f).toInt().coerceAtLeast(0)

    val hasAction = data.visuals.actionLabel != null
    Snackbar(
        action = {
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(label)
                }
            }
        },
        dismissAction = if (hasAction) {
            {
                CountdownCircle(
                    progress = progress,
                    seconds = remainingSeconds,
                )
            }
        } else {
            null
        },
    ) {
        Text(data.visuals.message)
    }
}

@Composable
private fun CountdownCircle(
    progress: Float,
    seconds: Int,
) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = seconds.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
