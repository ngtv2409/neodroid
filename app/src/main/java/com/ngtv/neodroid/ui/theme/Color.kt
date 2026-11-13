package com.ngtv.neodroid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * HSL palette. [tone] is a lightness percentage from 0 (black) to 100 (white),
 */
internal class HslPalette(private val hue: Float, private val saturation: Float) {
    operator fun get(tone: Int): Color = Color.hsl(hue, saturation, tone.coerceIn(0, 100) / 100f)
}

const val BRAND_HUE = 197f

private const val BRAND_SATURATION = 0.46f
private const val NEUTRAL_SATURATION = 0.05f

internal val brandPalette = HslPalette(BRAND_HUE, BRAND_SATURATION)
internal val neutralPalette = HslPalette(BRAND_HUE, NEUTRAL_SATURATION)
internal val errorPalette = HslPalette(0f, 0.65f)
internal val warningPalette = HslPalette(38f, 0.85f)
