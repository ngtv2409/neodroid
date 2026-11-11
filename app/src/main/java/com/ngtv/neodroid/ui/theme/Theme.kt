package com.ngtv.neodroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val lightColors = lightColorScheme(
    primary = gray100,
    onPrimary = gray1000,
    primaryContainer = gray900,
    onPrimaryContainer = gray100,
    secondary = gray600,
    onSecondary = gray0,
    secondaryContainer = gray800,
    onSecondaryContainer = gray100,
    tertiary = gray500,
    onTertiary = gray0,
    background = gray1000,
    onBackground = gray100,
    surface = gray1000,
    onSurface = gray100,
    surfaceVariant = gray900,
    onSurfaceVariant = gray400,
    surfaceContainerLowest = gray1000,
    surfaceContainerLow = gray1000,
    surfaceContainer = gray900,
    surfaceContainerHigh = gray800,
    surfaceContainerHighest = gray700,
    outline = gray700,
    outlineVariant = gray800,

    error = gray100,
    onError = gray1000,
    errorContainer = gray900,
    onErrorContainer = gray100,
)

private val darkColors = darkColorScheme(
    primary = gray1000,
    onPrimary = gray100,
    primaryContainer = gray100,
    onPrimaryContainer = gray1000,
    secondary = gray600,
    onSecondary = gray0,
    secondaryContainer = gray300,
    onSecondaryContainer = gray1000,
    tertiary = gray500,
    onTertiary = gray0,
    background = gray0,
    onBackground = gray1000,
    surface = gray0,
    onSurface = gray1000,
    surfaceVariant = gray200,
    onSurfaceVariant = gray600,
    surfaceContainerLowest = gray0,
    surfaceContainerLow = gray50,
    surfaceContainer = gray100,
    surfaceContainerHigh = gray200,
    surfaceContainerHighest = gray300,
    outline = gray400,
    outlineVariant = gray200,

    error = gray1000,
    onError = gray100,
    errorContainer = gray200,
    onErrorContainer = gray1000,
)

@Composable
fun NeodroidTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        typography = neodroidTypography,
        shapes = neodroidShapes,
        content = content,
    )
}
