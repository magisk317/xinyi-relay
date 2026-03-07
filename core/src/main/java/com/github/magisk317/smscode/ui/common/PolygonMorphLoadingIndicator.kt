package com.github.magisk317.smscode.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PolygonMorphLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = LoadingIndicatorTokens.ContainedSize,
) {
    ContainedLoadingIndicator(
        modifier = modifier.size(size),
    )
}
