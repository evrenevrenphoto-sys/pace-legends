package com.pace.legends.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PacePrimary,
    secondary = PaceSecondary,
    tertiary = PaceTertiary,
    background = PaceDarkBackground,
    surface = PaceDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = PaceTextPrimary,
    onSurface = PaceTextPrimary,
    error = PaceError
)

// We only support Dark Theme for now (F1 style)
@Composable
fun PaceLegendsTheme(
    // Dynamic color is available on Android 12+ but we might want to enforce brand colors
    dynamicColor: Boolean = false, 
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // Use dynamic dark scheme if requested, else fallback to brand
            dynamicDarkColorScheme(context) 
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PaceTypography,
        content = content
    )
}
