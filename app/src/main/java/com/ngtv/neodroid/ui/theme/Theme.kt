package com.ngtv.neodroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val lightColors = lightColorScheme(
    primary = brandPalette[30],
    onPrimary = neutralPalette[100],
    primaryContainer = brandPalette[92],
    onPrimaryContainer = brandPalette[15],
    secondary = brandPalette[38],
    onSecondary = neutralPalette[100],
    secondaryContainer = brandPalette[90],
    onSecondaryContainer = brandPalette[18],
    tertiary = brandPalette[42],
    onTertiary = neutralPalette[100],
    tertiaryContainer = brandPalette[88],
    onTertiaryContainer = brandPalette[18],
    background = neutralPalette[99],
    onBackground = neutralPalette[10],
    surface = neutralPalette[99],
    onSurface = neutralPalette[10],
    surfaceVariant = neutralPalette[92],
    onSurfaceVariant = neutralPalette[38],
    surfaceContainerLowest = neutralPalette[100],
    surfaceContainerLow = neutralPalette[98],
    surfaceContainer = neutralPalette[96],
    surfaceContainerHigh = neutralPalette[93],
    surfaceContainerHighest = neutralPalette[90],
    outline = neutralPalette[58],
    outlineVariant = neutralPalette[82],
    error = errorPalette[42],
    onError = neutralPalette[100],
    errorContainer = errorPalette[93],
    onErrorContainer = errorPalette[26],
    inverseSurface = neutralPalette[18],
    inverseOnSurface = neutralPalette[94],
    inversePrimary = brandPalette[70],
    surfaceTint = brandPalette[49],
    scrim = neutralPalette[0],
)

private val darkColors = darkColorScheme(
    primary = brandPalette[49],
    onPrimary = neutralPalette[8],
    primaryContainer = brandPalette[22],
    onPrimaryContainer = brandPalette[92],
    secondary = brandPalette[70],
    onSecondary = neutralPalette[12],
    secondaryContainer = brandPalette[15],
    onSecondaryContainer = brandPalette[92],
    tertiary = brandPalette[70],
    onTertiary = neutralPalette[12],
    tertiaryContainer = brandPalette[15],
    onTertiaryContainer = brandPalette[92],
    background = neutralPalette[6],
    onBackground = neutralPalette[94],
    surface = neutralPalette[6],
    onSurface = neutralPalette[94],
    surfaceVariant = neutralPalette[18],
    onSurfaceVariant = neutralPalette[70],
    surfaceContainerLowest = neutralPalette[4],
    surfaceContainerLow = neutralPalette[8],
    surfaceContainer = neutralPalette[11],
    surfaceContainerHigh = neutralPalette[15],
    surfaceContainerHighest = neutralPalette[19],
    outline = neutralPalette[48],
    outlineVariant = neutralPalette[24],
    error = errorPalette[70],
    onError = errorPalette[12],
    errorContainer = errorPalette[24],
    onErrorContainer = errorPalette[88],
    inverseSurface = neutralPalette[92],
    inverseOnSurface = neutralPalette[12],
    inversePrimary = brandPalette[30],
    surfaceTint = brandPalette[49],
    scrim = neutralPalette[0],
)

@Composable
fun NeodroidTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) darkColors else lightColors
    val neodroidColors = if (darkTheme) darkNeodroidColors else lightNeodroidColors

    CompositionLocalProvider(LocalNeodroidColors provides neodroidColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = neodroidTypography,
            shapes = neodroidShapes,
            content = content,
        )
    }
}
