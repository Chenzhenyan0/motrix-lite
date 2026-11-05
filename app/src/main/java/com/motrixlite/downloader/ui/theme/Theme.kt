package com.motrixlite.downloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.motrixlite.downloader.data.model.ThemeMode

private val BluePrimary = Color(0xFF6EA8FF)
private val BluePrimaryContainer = Color(0xFF1E3A5F)
private val DarkBackground = Color(0xFF0E1116)
private val DarkSurface = Color(0xFF161B22)
private val DarkSurfaceVariant = Color(0xFF222A34)
private val DarkOutline = Color(0xFF3A4450)

private val DarkColors = darkColorScheme(
    primary = BluePrimary,
    onPrimary = Color(0xFF00214A),
    primaryContainer = BluePrimaryContainer,
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF7FD1C1),
    onSecondary = Color(0xFF00382F),
    tertiary = Color(0xFFF5C76B),
    background = DarkBackground,
    onBackground = Color(0xFFE6EDF3),
    surface = DarkSurface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA9B4C0),
    outline = DarkOutline,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D5FD1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF00695C),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF161B22),
    surface = Color.White,
    onSurface = Color(0xFF161B22),
    surfaceVariant = Color(0xFFE7EBF0),
    onSurfaceVariant = Color(0xFF4A5563),
    outline = Color(0xFFB7C0CB),
    error = Color(0xFFBA1A1A),
)

@Composable
fun MotrixLiteTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
