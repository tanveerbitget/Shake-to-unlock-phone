package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF00242B),
    primaryContainer = Color(0xFF004B59),
    onPrimaryContainer = Color(0xFF9DF0FF),
    secondary = EmeraldStatus,
    onSecondary = Color(0xFF002116),
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = Color(0xFF6EE7B7),
    tertiary = AmberWarning,
    onTertiary = Color(0xFF2E1A00),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CoralDanger,
    onError = Color.White,
    errorContainer = CoralContainerDark,
    onErrorContainer = Color(0xFFFCA5A5),
    background = DeepNavyBg,
    onBackground = Color(0xFFE8EFFA),
    surface = NavySurface,
    onSurface = Color(0xFFE8EFFA),
    surfaceVariant = NavySurfaceHigh,
    onSurfaceVariant = Color(0xFF9FB0CE),
    surfaceContainerLow = NavySurface,
    surfaceContainer = NavySurfaceHigh,
    surfaceContainerHigh = NavySurfaceHighest,
    outline = NavyOutline,
    outlineVariant = Color(0xFF1F2E4C)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F0FD),
    onPrimaryContainer = Color(0xFF003549),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = Color(0xFF7F1D1D),
    background = LightIceBg,
    onBackground = Color(0xFF0F172A),
    surface = LightCardSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLow = LightCardSurface,
    surfaceContainer = LightSurfaceHigh,
    surfaceContainerHigh = Color(0xFFDCE6F7),
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

private val ShakeLockShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ShakeLockTheme(
    darkTheme: Boolean = true, // Precision Hardware Dark by default, supports light mode too
    useSystemTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = if (useSystemTheme) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShakeLockTypography,
        shapes = ShakeLockShapes,
        content = content
    )
}
