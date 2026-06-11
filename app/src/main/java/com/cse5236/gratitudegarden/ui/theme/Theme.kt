package com.cse5236.gratitudegarden.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Positivity Garden is a warm, light, illustrated experience. We use a fixed
// light scheme built from the PG palette rather than Material dynamic color so
// the garden greens read consistently across devices.
private val GardenLightColors = lightColorScheme(
    primary = PgPrimary,
    onPrimary = PgBgCream,
    primaryContainer = PgMoss,
    onPrimaryContainer = PgPrimaryDeep,
    secondary = PgAccent,
    onSecondary = PgBgCream,
    background = PgBgSage,
    onBackground = PgInk,
    surface = PgBgCream,
    onSurface = PgInk,
    surfaceVariant = PgMoss,
    onSurfaceVariant = PgInkSoft,
    outline = PgMoss,
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
