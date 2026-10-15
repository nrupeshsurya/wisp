package com.alarmclockpro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette for Android < 12 (no wallpaper-based dynamic colour): a calm teal.
private val LightColors = lightColorScheme(
    primary = Color(0xFF006A6A), onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1F0), onPrimaryContainer = Color(0xFF002020),
    secondaryContainer = Color(0xFFCCE8E7), onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C), tertiaryContainer = Color(0xFFD3E4FF),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5D4), onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F), onPrimaryContainer = Color(0xFF9CF1F0),
    secondaryContainer = Color(0xFF324B4B), onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8), tertiaryContainer = Color(0xFF334863),
)

@Composable
fun ClockTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
