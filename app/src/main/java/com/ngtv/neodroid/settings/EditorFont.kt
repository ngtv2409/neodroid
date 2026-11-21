package com.ngtv.neodroid.settings

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ngtv.neodroid.R

/**
 * Fonts bundled in the app
 *
 * Keys must be stable, they are persisted in settings
 */
enum class EditorFont(val key: String, val label: String, val fontRes: Int) {
    JetBrainsMono(
        key = "jetbrains_mono",
        label = "JetBrains Mono",
        fontRes = R.font.jetbrains_mono_nerdfont,
    ),
    FiraCode(
        key = "fira_code",
        label = "Fira Code",
        fontRes = R.font.fira_code_nerdfont,
    ),
    Hack(
        key = "hack",
        label = "Hack",
        fontRes = R.font.hack_nerdfont,
    ),
    SourceCodePro(
        key = "source_code_pro",
        label = "Source Code Pro",
        fontRes = R.font.source_code_pro_nerdfont,
    ),
    ;

    fun fontFamily(): FontFamily = FontFamily(Font(fontRes))

    companion object {
        fun fromKey(key: String): EditorFont =
            entries.firstOrNull { font -> font.key == key } ?: JetBrainsMono
    }
}
