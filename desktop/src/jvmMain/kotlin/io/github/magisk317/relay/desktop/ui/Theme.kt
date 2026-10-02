package io.github.magisk317.relay.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** WebUI brand palette (frontend/webui index.css) mirrored for the desktop shell. */
private val XinyiGreen = Color(0xFF87AD1E)
private val XinyiGreenDark = Color(0xFF6F8E18)
private val XinyiInk = Color(0xFF243115)
private val XinyiInkSoft = Color(0xFF596743)
private val XinyiPaper = Color(0xFFFBFEF2)
private val XinyiPaperDim = Color(0xFFF0F8D9)
private val XinyiLeaf = Color(0xFFD8E9A6)
private val XinyiError = Color(0xFFB24A24)

private val XinyiLightColors = lightColorScheme(
    primary = XinyiGreenDark,
    onPrimary = Color(0xFFF8FFE6),
    primaryContainer = XinyiLeaf,
    onPrimaryContainer = XinyiInk,
    secondary = XinyiGreen,
    onSecondary = Color(0xFF1F2A10),
    secondaryContainer = XinyiPaperDim,
    onSecondaryContainer = XinyiInk,
    background = XinyiPaper,
    onBackground = XinyiInk,
    surface = Color(0xFFFFFFFF),
    onSurface = XinyiInk,
    surfaceVariant = XinyiPaperDim,
    onSurfaceVariant = XinyiInkSoft,
    outline = XinyiLeaf,
    outlineVariant = Color(0xFFE7F1CB),
    error = XinyiError,
    onError = Color(0xFFFFF8F3),
)

private val XinyiDarkColors = darkColorScheme(
    primary = XinyiGreen,
    onPrimary = Color(0xFF1F2A10),
    primaryContainer = Color(0xFF44591A),
    onPrimaryContainer = Color(0xFFF4FBE0),
    secondary = Color(0xFFA8CB4E),
    onSecondary = Color(0xFF1F2A10),
    secondaryContainer = Color(0xFF37451C),
    onSecondaryContainer = Color(0xFFF4FBE0),
    background = Color(0xFF161B0E),
    onBackground = Color(0xFFF4FBE0),
    surface = Color(0xFF1E2413),
    onSurface = Color(0xFFF4FBE0),
    surfaceVariant = Color(0xFF2C351B),
    onSurfaceVariant = Color(0xFFCBD9B4),
    outline = Color(0xFF5A6B3A),
    outlineVariant = Color(0xFF3D4A26),
    error = Color(0xFFE8907A),
    onError = Color(0xFF3B1305),
)

private val XinyiShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val XinyiTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun XinyiDesktopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) XinyiDarkColors else XinyiLightColors,
        shapes = XinyiShapes,
        typography = XinyiTypography,
        content = content,
    )
}
