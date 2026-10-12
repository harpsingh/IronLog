package com.ironlog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

val Bg = Color(0xFF0E0F12)
val Surface1 = Color(0xFF17191E)
val Surface2 = Color(0xFF22252C)
val Accent = Color(0xFFFF6B2C)
val AccentSoft = Color(0xFF3A2218)
val Success = Color(0xFF3DDC84)
val OnDark = Color(0xFFF2F3F5)
val Muted = Color(0xFF9AA0AA)

private val IronColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Color(0xFFFFD9C7),
    secondary = Success,
    onSecondary = Color.Black,
    background = Bg,
    onBackground = OnDark,
    surface = Surface1,
    onSurface = OnDark,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    outline = Color(0xFF3A3E47),
    error = Color(0xFFFF5449),
)

private val IronTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

private val IronShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Dark-only theme: high contrast, large touch targets for gym use. */
@Composable
fun IronLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IronColors, typography = IronTypography, shapes = IronShapes, content = content)
}
