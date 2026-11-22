package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GeometricSage,
    secondary = GeometricClay,
    tertiary = HighlightGreenButton,
    background = GeometricSoftBg,
    surface = Color.White,
    surfaceVariant = GeometricSoftBg,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = GeometricDark,
    onSurface = GeometricDark,
    onSurfaceVariant = GeometricDark
)

private val LightColorScheme = lightColorScheme(
    primary = GeometricSage,
    secondary = GeometricClay,
    tertiary = HighlightGreenButton,
    background = GeometricSoftBg,
    surface = Color.White,
    surfaceVariant = GeometricSoftBg,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = GeometricDark,
    onSurface = GeometricDark,
    onSurfaceVariant = GeometricDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
