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
import io.github.magisk317.relay.desktop.session.DesktopAppearance

/** Muted Xinyi green on quiet neutral surfaces, shared with the Web console. */
private val XinyiGreen = Color(0xFF718A34)
private val XinyiGreenDark = Color(0xFF5E7627)
private val XinyiInk = Color(0xFF252C22)
private val XinyiInkSoft = Color(0xFF5D6658)
private val XinyiPaper = Color(0xFFF3F5F0)
private val XinyiPaperDim = Color(0xFFE9EDE6)
private val XinyiLeaf = Color(0xFFE0E9D0)
private val XinyiError = Color(0xFF9F3D35)

private val XinyiLightColors = lightColorScheme(
    primary = XinyiGreenDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = XinyiLeaf,
    onPrimaryContainer = XinyiInk,
    secondary = XinyiGreen,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = XinyiPaperDim,
    onSecondaryContainer = XinyiInk,
    background = XinyiPaper,
    onBackground = XinyiInk,
    surface = Color(0xFFFFFFFF),
    onSurface = XinyiInk,
    surfaceVariant = XinyiPaperDim,
    onSurfaceVariant = XinyiInkSoft,
    outline = Color(0xFFBBC5B4),
    outlineVariant = Color(0xFFD7DED1),
    error = XinyiError,
    onError = Color(0xFFFFF8F3),
)

private val XinyiDarkColors = darkColorScheme(
    primary = Color(0xFFB5CA82),
    onPrimary = Color(0xFF273019),
    primaryContainer = Color(0xFF394629),
    onPrimaryContainer = Color(0xFFE3EDCF),
    secondary = Color(0xFFBBC8A1),
    onSecondary = Color(0xFF293126),
    secondaryContainer = Color(0xFF343C30),
    onSecondaryContainer = Color(0xFFE0E6D8),
    background = Color(0xFF171B16),
    onBackground = Color(0xFFE7EBE3),
    surface = Color(0xFF20251F),
    onSurface = Color(0xFFE7EBE3),
    surfaceVariant = Color(0xFF2A3029),
    onSurfaceVariant = Color(0xFFC2C9BC),
    outline = Color(0xFF687363),
    outlineVariant = Color(0xFF414940),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF571D17),
    errorContainer = Color(0xFF73332D),
    onErrorContainer = Color(0xFFFFDAD4),
)

private val XinyiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

private val XinyiTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun XinyiDesktopTheme(
    appearance: DesktopAppearance = DesktopAppearance.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = appearance.isDark(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (darkTheme) XinyiDarkColors else XinyiLightColors,
        shapes = XinyiShapes,
        typography = XinyiTypography,
        content = content,
    )
}
