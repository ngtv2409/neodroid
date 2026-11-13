package com.ngtv.neodroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-specific colors that Material's color scheme does not model.
 *
 * These are for status and editor chrome, not for general surfaces. Keep them
 * sparse so the neutral layout remains the dominant visual structure.
 */
@Immutable
data class NeodroidColors(
    val online: Color,
    val warning: Color,
    val info: Color,
    val cursor: Color,
)

internal val lightNeodroidColors = NeodroidColors(
    online = brandPalette[30],
    warning = warningPalette[32],
    info = brandPalette[38],
    cursor = brandPalette[30],
)

internal val darkNeodroidColors = NeodroidColors(
    online = brandPalette[60],
    warning = warningPalette[62],
    info = brandPalette[68],
    cursor = brandPalette[70],
)

internal val LocalNeodroidColors = staticCompositionLocalOf { lightNeodroidColors }

val MaterialTheme.neodroidColors: NeodroidColors
    @Composable
    @ReadOnlyComposable
    get() = LocalNeodroidColors.current
