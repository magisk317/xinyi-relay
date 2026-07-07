package io.github.magisk317.relay.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppIconBitmapImage(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
    fallbackIcon: ImageVector = Icons.Default.Build,
) {
    if (bitmap == null) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = MaterialTheme.colorScheme.outline,
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(MaterialTheme.shapes.small),
        )
    }
}
