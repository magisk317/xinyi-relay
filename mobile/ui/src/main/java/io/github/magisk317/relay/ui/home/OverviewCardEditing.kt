package io.github.magisk317.relay.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.core.R
import kotlin.math.PI
import kotlinx.coroutines.launch

@Composable
internal fun HomeCardContainer(
    editMode: Boolean,
    wiggleKey: String,
    isDragging: Boolean,
    dragOffsetY: Float,
    onLongPress: () -> Unit,
    onRemove: (() -> Unit)?,
    onDragStart: (() -> Unit)?,
    onDragEnd: (() -> Unit)?,
    onDrag: ((Float) -> Unit)?,
    content: @Composable () -> Unit,
) {
    val wiggleParams = remember(wiggleKey) {
        val random = kotlin.random.Random(wiggleKey.hashCode())
        val amplitudeFactor = 0.7f + random.nextFloat() * 0.3f
        val startOffsetMs = random.nextInt(120)
        WiggleParams(
            amplitudeFactor = amplitudeFactor,
            startOffsetMs = startOffsetMs,
        )
    }
    val wiggleTransition = rememberInfiniteTransition(label = "overviewWiggle")
    val wiggleValue by wiggleTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 120,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                wiggleParams.startOffsetMs,
                StartOffsetType.FastForward,
            ),
        ),
        label = "wigglePhase",
    )
    val removeScale = remember { Animatable(1f) }
    val removeAlpha = remember { Animatable(1f) }
    var removing by remember { mutableStateOf(false) }
    val wiggleEnabled = editMode && !isDragging && !removing
    val rotation = if (wiggleEnabled) {
        val degreesPerRad = (180f / PI.toFloat())
        wiggleValue * 0.012f * wiggleParams.amplitudeFactor * degreesPerRad
    } else {
        0f
    }
    val translationY = if (isDragging) dragOffsetY else 0f
    val dragScale = if (isDragging) 1.04f else 1f
    val scale = dragScale * removeScale.value
    val alpha = if (isDragging) 0.85f else removeAlpha.value
    val scope = rememberCoroutineScope()
    val editModeState by rememberUpdatedState(editMode)
    val onLongPressState by rememberUpdatedState(onLongPress)
    val onDragStartState by rememberUpdatedState(onDragStart)
    val onDragEndState by rememberUpdatedState(onDragEnd)
    val onDragState by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(editMode) {
                if (onDragState != null) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress != null) {
                            if (!editModeState) {
                                onLongPressState()
                            }
                            XLog.i("Overview drag longPress key=%s editMode=%s", wiggleKey, editModeState)
                            onDragStartState?.invoke()
                            var loggedMove = false
                            try {
                                drag(longPress.id) { change ->
                                    val delta = change.positionChange()
                                    if (delta.y != 0f) {
                                        if (!loggedMove) {
                                            loggedMove = true
                                            XLog.i("Overview drag move key=%s dy=%.2f", wiggleKey, delta.y)
                                        }
                                        onDragState?.invoke(delta.y)
                                        change.consume()
                                    }
                                }
                            } finally {
                                XLog.i("Overview drag end key=%s moved=%s", wiggleKey, loggedMove)
                                onDragEndState?.invoke()
                            }
                        }
                    }
                } else {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress != null) {
                            onLongPress()
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                rotationZ = rotation
                this.translationY = translationY
                shadowElevation = if (isDragging) 10f else 0f
            },
    ) {
        content()
        if (editMode && !removing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .shadow(3.dp, CircleShape, clip = false)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = onRemove != null) {
                        if (removing || onRemove == null) return@clickable
                        removing = true
                        scope.launch {
                            removeScale.snapTo(1f)
                            removeAlpha.snapTo(1f)
                            val spec = tween<Float>(
                                durationMillis = 300,
                                easing = FastOutLinearInEasing,
                            )
                            val scaleJob = launch { removeScale.animateTo(0.4f, spec) }
                            val alphaJob = launch { removeAlpha.animateTo(0f, spec) }
                            scaleJob.join()
                            alphaJob.join()
                            onRemove.invoke()
                        }
                    }
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.remove),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private data class WiggleParams(
    val amplitudeFactor: Float,
    val startOffsetMs: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddOverviewCardSheet(
    specs: List<HomeCardSpec>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.home_add_card_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (specs.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.home_add_card_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(specs, key = { it.id }) { spec ->
                        AddOverviewCardItem(
                            spec = spec,
                            onAdd = { onAdd(spec.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddOverviewCardItem(
    spec: HomeCardSpec,
    onAdd: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(id = spec.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(id = R.string.home_add_card_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FilledTonalIconButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(id = R.string.forward_filter_action_add),
            )
        }
    }
}
