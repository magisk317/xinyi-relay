package io.github.magisk317.relay.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

@Composable
fun AppIconImage(
    packageName: String?,
    label: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
    fallbackIcon: ImageVector = Icons.Default.Build,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetIconPx = remember(size, density) {
        with(density) { size.roundToPx() }.coerceIn(1, MAX_ICON_SIZE_PX)
    }
    val imageLoader = remember(context) { AppIconLoader.imageLoader(context) }
    val request = remember(packageName, label, targetIconPx) {
        AppIconRequest(
            packageName = packageName,
            label = label,
            sizePx = targetIconPx,
        )
    }
    val showFallback = packageName.isNullOrBlank() && label.isNullOrBlank()

    Box(modifier = modifier.size(size)) {
        if (showFallback) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.outline,
            )
        } else {
            SubcomposeAsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(size)
                    .clip(MaterialTheme.shapes.small),
                loading = {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(size),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                },
                error = {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(size),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                },
            )
        }
    }
}

private const val MAX_ICON_SIZE_PX = 256
