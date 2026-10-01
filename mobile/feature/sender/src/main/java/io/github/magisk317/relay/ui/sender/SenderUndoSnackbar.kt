package io.github.magisk317.relay.ui.sender

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.common.AppSnackbarData
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val SENDER_UNDO_SNACKBAR_DURATION_MS = 5_000L

private const val UNDO_COUNTDOWN_TICK_MS = 50L

/**
 * Track-neutral undo snackbar: one shared body rendered through kit surfaces,
 * text and buttons so both UI kit styles get the same layout and colours.
 *
 * The caller keeps the standard snackbar contract - [AppSnackbarData] drives
 * the action and dismissal - while the countdown ring replaces the dismiss
 * affordance, so there is no per-track fork here.
 */
@Composable
internal fun UndoCountdownSnackbar(
    data: AppSnackbarData,
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

    val scope = rememberCoroutineScope()
    val actionLabel = data.visuals.actionLabel
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        color = appColor(AppColorRole.SurfaceContainerHigh),
        contentColor = appColor(AppColorRole.OnSurface),
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = data.visuals.message,
                modifier = Modifier.weight(1f),
                role = AppTextRole.Body,
            )
            if (actionLabel != null) {
                AppTextButton(
                    text = actionLabel,
                    onClick = { scope.launch { data.performAction() } },
                    color = appColor(AppColorRole.Primary),
                )
                CountdownCircle(
                    progress = progress,
                    seconds = remainingSeconds,
                )
            }
        }
    }
}

@Composable
private fun CountdownCircle(
    progress: Float,
    seconds: Int,
) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppCircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 2.dp,
            color = appColor(AppColorRole.Primary),
            trackColor = appColor(AppColorRole.SurfaceVariant),
        )
        AppText(
            text = seconds.toString(),
            role = AppTextRole.Footnote,
            color = appColor(AppColorRole.Primary),
        )
    }
}
