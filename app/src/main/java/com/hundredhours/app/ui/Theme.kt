package com.hundredhours.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hundredhours.app.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF2E6B57),
    onPrimary = Color.White,
    background = Color(0xFFF8F7F4),
    onBackground = Color(0xFF15171A),
    surface = Color(0xFFF8F7F4),
    onSurface = Color(0xFF15171A),
    surfaceVariant = Color(0xFFE6E4DF),
    onSurfaceVariant = Color(0xFF5E6166),
    outline = Color(0xFF7A7D82),
    outlineVariant = Color(0xFFE0DED9), // dividers
    surfaceContainer = Color(0xFFFFFFFF), // menus
    surfaceContainerHigh = Color(0xFFFFFFFF), // dialogs
)

private val Dark = darkColorScheme(
    primary = Color(0xFF86D1B3),
    onPrimary = Color(0xFF0B2A20),
    background = Color(0xFF0F1112),
    onBackground = Color(0xFFE9EAEB),
    surface = Color(0xFF0F1112),
    onSurface = Color(0xFFE9EAEB),
    surfaceVariant = Color(0xFF26292C),
    onSurfaceVariant = Color(0xFF9EA2A7),
    outline = Color(0xFF8D9196),
    outlineVariant = Color(0xFF2E3235), // dividers
    surfaceContainer = Color(0xFF1A1D1F), // menus
    surfaceContainerHigh = Color(0xFF1A1D1F), // dialogs
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(mode)) Dark else Light, content = content)
}
