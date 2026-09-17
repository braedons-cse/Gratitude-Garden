package com.gratitudegarden.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Gratitude Garden is a warm, light, illustrated experience. We use a fixed
// light scheme built from the GG palette rather than Material dynamic color so
// the garden greens read consistently across devices.
private val GardenLightColors = lightColorScheme(
    primary = GgPrimary,
    onPrimary = GgBgCream,
    primaryContainer = GgMoss,
    onPrimaryContainer = GgPrimaryDeep,
    secondary = GgAccent,
    onSecondary = GgBgCream,
    background = GgBgSage,
    onBackground = GgInk,
    surface = GgBgCream,
    onSurface = GgInk,
    surfaceVariant = GgMoss,
    onSurfaceVariant = GgInkSoft,
    outline = GgMoss,
)

@Composable
fun GratitudeGardenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Onboarding (and the rest of the app, for now) is designed in the "day"
    // palette, so we stay light regardless of the system setting.
    MaterialTheme(
        colorScheme = GardenLightColors,
        typography = Typography,
        content = content,
    )
}
