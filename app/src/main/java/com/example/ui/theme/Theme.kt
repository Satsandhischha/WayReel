package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush

private val WayReelColorScheme = darkColorScheme(
    primary = AmberBright,
    onPrimary = NavyDeep,
    primaryContainer = AmberMuted,
    onPrimaryContainer = AmberBright,
    secondary = AmberOrange,
    onSecondary = NavyDeep,
    secondaryContainer = IndigoSurface,
    onSecondaryContainer = TextPrimary,
    tertiary = ConfidenceHigh,
    background = NavyDeep,
    onBackground = TextPrimary,
    surface = NavyMid,
    onSurface = TextPrimary,
    surfaceVariant = IndigoDeep,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    error = ErrorCoral,
    onError = NavyDeep
)

val NavyIndigoGradient = Brush.verticalGradient(
    colors = listOf(NavyDeep, NavyMid, IndigoDeep)
)

val AmberGradient = Brush.horizontalGradient(
    colors = listOf(AmberBright, AmberOrange)
)

@Composable
fun WayReelTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WayReelColorScheme,
        typography = AppTypography,
        content = content
    )
}
