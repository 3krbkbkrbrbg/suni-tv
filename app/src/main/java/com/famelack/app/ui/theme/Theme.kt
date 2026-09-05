package com.famelack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Famelack palette — dark mode first
private val Bg = Color(0xFF0E1014)
private val BgElevated = Color(0xFF161A20)
private val BgSurface = Color(0xFF1F242C)
private val Accent = Color(0xFF4DD0E1)
private val AccentAmber = Color(0xFFFFB74D)
private val TextPrimary = Color(0xFFE8EAF0)
private val TextSecondary = Color(0xFF8A93A6)
private val Error = Color(0xFFEF5350)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF002024),
    primaryContainer = Color(0xFF004D55),
    onPrimaryContainer = Color(0xFFB6F5FF),
    secondary = AccentAmber,
    onSecondary = Color(0xFF3D2400),
    tertiary = Color(0xFFB39DDB),
    background = Bg,
    onBackground = TextPrimary,
    surface = BgElevated,
    onSurface = TextPrimary,
    surfaceVariant = BgSurface,
    onSurfaceVariant = TextSecondary,
    error = Error,
    onError = Color.White
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00838F),
    secondary = Color(0xFFE65100),
    background = Color(0xFFFAFAFA),
    surface = Color.White
)

private val FamelackTypography = Typography(
    displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = TextSecondary),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun FamelackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = FamelackTypography, content = content)
}
