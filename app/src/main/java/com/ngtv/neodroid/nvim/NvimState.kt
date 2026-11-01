package com.ngtv.neodroid.nvim

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.ngtv.neodroid.core.events.HighlightAttr
import com.ngtv.neodroid.core.events.ModeProperty
import java.util.ArrayList
import java.util.IdentityHashMap
import java.util.LinkedHashMap

data class DefaultColorsClass(val foreground: Long, val background: Long, val special: Long)

/* Hold neovim UI states for rendering */
data class NvimState(
    val options: MutableMap<String, Any?> = mutableMapOf(),
    var modeInfo: List<ModeProperty> = mutableListOf(),
    var cursorStyleEnabled: Boolean = true,
    var modeIndex: Long = 0,
    var isBusy: Boolean = false,

    var defaultColors: SpanStyle = SpanStyle(color = Color.White, background = Color.Black),
    val highlightStyles: MutableMap<Int, SpanStyle> = mutableMapOf(),

    var grid: MutableList<MutableList<GridCell>>? = null,
) {
    fun deepCopy(): NvimState = this.copy(
        options = deepCopyValue(options),
        modeInfo = modeInfo.map { it.copy() },
        defaultColors = defaultColors.copy(),
        highlightStyles = highlightStyles.mapValuesTo(mutableMapOf()) { (_, item) ->
            item.copy()
        },
        grid = grid?.map { row ->
            row.toMutableList()
        }?.toMutableList(),
    )
}

/**
 * Deep-copies values supported by the MessagePack RPC implementation.
 */

@Suppress("UNCHECKED_CAST")
internal fun <T> deepCopyValue(value: T): T {
    if (value == null) return null as T
    return internalDeepCopyValue(value) as T
}
private fun internalDeepCopyValue(value: Any?): Any? = when (value) {
    null -> null

    is Boolean,
    is Byte,
    is Short,
    is Int,
    is Long,
    is Float,
    is Double,
    is String,
    -> value

    is ByteArray -> value.copyOf()

    is List<*> -> value.map(::deepCopyValue)

    is Map<*, *> -> value.entries.associateTo(LinkedHashMap()) {
        deepCopyValue(it.key) to deepCopyValue(it.value)
    }

    else -> error(
        "Unsupported type for deep copy: ${value::class.qualifiedName}",
    )
}

data class GridCell(val character: String, val highlightId: Int)

fun NvimState.hlIdToStyle(hlId: Int): SpanStyle {
    if (hlId == -1) {
        return defaultColors
    }
    return highlightStyles[hlId] ?: defaultColors
}

fun nvimRgbColor(rgb: Long): Color = Color(rgb.toInt() or 0xFF000000.toInt())

fun nvimAttrToStyle(hl: HighlightAttr): SpanStyle = SpanStyle(
    color = hl.foreground?.let { nvimRgbColor(it) }
        ?: Color.Unspecified,
    background = hl.background?.let { nvimRgbColor(it) }
        ?: Color.Unspecified,
    fontWeight = if (hl.bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (hl.italic) FontStyle.Italic else FontStyle.Normal,
    textDecoration = buildList {
        if (hl.underline) add(TextDecoration.Underline)
        if (hl.strikethrough) add(TextDecoration.LineThrough)
    }.let {
        when (it.size) {
            0 -> TextDecoration.None
            1 -> it.first()
            else -> TextDecoration.combine(it)
        }
    },
)
