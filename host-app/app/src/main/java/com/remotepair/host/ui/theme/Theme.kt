package com.remotepair.host.ui.theme

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

private val BlueAccent = Color(0xFF3B82F6)

private val Dark = darkColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2563EB),
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

private val Light = lightColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    background = Color(0xFFFBFBFD),
    onBackground = Color(0xFF0B0E14),
    surface = Color.White,
    onSurface = Color(0xFF0B0E14),
)

val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)

@Composable
fun RemotePairHostTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
