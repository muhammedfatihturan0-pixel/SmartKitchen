package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GeometricSage,
    secondary = GeometricClay,
    tertiary = HighlightGreenButton,
    background = GeometricDark,
    surface = GeometricDark,
    onPrimary = GeometricSoftBg,
    onSecondary = GeometricSoftBg,
    onBackground = GeometricSoftBg,
    onSurface = GeometricSoftBg
)

private val LightColorScheme = lightColorScheme(
    primary = GeometricSage,
    secondary = GeometricClay,
    tertiary = HighlightGreenButton,
    background = GeometricSoftBg,
    surface = GeometricSoftBg,
    onPrimary = GeometricSoftBg,
    onSecondary = GeometricDark,
    onBackground = GeometricDark,
    onSurface = GeometricDark
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
