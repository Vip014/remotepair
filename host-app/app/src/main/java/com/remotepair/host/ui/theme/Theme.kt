package com.remotepair.host.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlueAccent = Color(0xFF3B82F6)
private val BlueAccentDark = Color(0xFF2563EB)

private val DarkColors = darkColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    primaryContainer = BlueAccentDark,
    onPrimaryContainer = Color.White,
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF121620),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1C212D),
    onSurfaceVariant = Color(0xFF9AA1AD),
    outline = Color(0xFF2A3140),
    error = Color(0xFFEF4444),
)

private val LightColors = lightColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    background = Color(0xFFFBFBFD),
    onBackground = Color(0xFF0B0E14),
    surface = Color.White,
    onSurface = Color(0xFF0B0E14),
)

@Composable
fun RemotePairTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
