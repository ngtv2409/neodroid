package com.ngtv.neodroid.nvim

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.ngtv.neodroid.core.events.HighlightAttr
import com.ngtv.neodroid.core.events.ModeProperty
import com.ngtv.neodroid.core.events.NvimUiEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.BreakIterator
import java.util.ArrayList

data class CursorState(val row: Int = 0, val col: Int = 0)

/**
 * A collection of text with the same hl group.
 *
 * columns is the number of grid cells occupied by text.
 * In Neovim's `ext_linegrid` protocol a double-width character is sent in its left cell and the following cell contains an empty string.
 * We merge that filler into a single wide span occupying two columns
 */
@Immutable
class TextSpan(
    val startCol: Int,
    val columns: Int,
    val text: String,
    val hlId: Int,
    val wide: Boolean = false,
) {
    val endCol: Int
        get() = startCol + columns

    val layoutHash: Int = 31 * (31 * text.hashCode() + hlId) + if (wide) 1 else 0

    /**
     * Extracts columns [start, end) from this span.
     *
     * Narrow text is split at grapheme cluster boundaries so combining marks and
     * surrogate pairs belong to their cell.
     * A wide character cannot be split: overlap returns null, caller fills the gap with a blank cell.
     */
    fun slice(start: Int, end: Int): TextSpan? {
        val from = maxOf(start, startCol)
        val to = minOf(end, endCol)
        if (from >= to) return null

        if (wide) {
            return if (from == startCol && to == endCol) this else null
        }

        val fromOffset = charOffsetAt(from - startCol)
        val toOffset = charOffsetAt(to - startCol)
        if (fromOffset >= toOffset) return null

        return TextSpan(
            startCol = from,
            columns = to - from,
            text = text.substring(fromOffset, toOffset),
            hlId = hlId,
        )
    }

    /**
     * Returns the text of the cell at offset, or null when offset is not a cell in this span.
     */
    fun cellTextAt(offset: Int): String? {
        if (offset !in 0 until columns) return null
        if (wide) return if (offset == 0) text else null

        val from = charOffsetAt(offset)
        val to = if (offset + 1 >= columns) text.length else charOffsetAt(offset + 1)
        return if (from < to) text.substring(from, to) else null
    }

    private fun charOffsetAt(columnOffset: Int): Int {
        val offset = columnOffset.coerceIn(0, columns)
        if (offset == 0) return 0
        if (offset == columns) return text.length
        if (text.length == columns) return offset

        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var cluster = 0
        var position = iterator.first()
        while (position != BreakIterator.DONE && cluster < offset) {
            position = iterator.next()
            cluster++
        }
        return if (position == BreakIterator.DONE) text.length else position
    }

    companion object {
        fun blank(startCol: Int, columns: Int): TextSpan = TextSpan(
            startCol = startCol,
            columns = columns,
            text = " ".repeat(columns),
            hlId = -1,
        )
    }
}

@Immutable
class TextRow(val spans: List<TextSpan>, val dirtyStart: Int = -1, val dirtyEnd: Int = -1) {
    val columns: Int
        get() = spans.sumOf { it.columns }

    fun patch(startCol: Int, replacement: List<TextSpan>): TextRow {
        if (replacement.isEmpty()) return this

        val start = startCol.coerceAtLeast(0)
        val end = start + replacement.sumOf { it.columns }
        val width = maxOf(columns, end)
        val result = ArrayList<TextSpan>(spans.size + replacement.size + 2)

        spans.forEach { span ->
            when {
                span.endCol <= start -> result.add(span)

                span.startCol >= end -> result.add(span)

                else -> {
                    span.slice(span.startCol, start)?.let(result::add)
                    span.slice(end, span.endCol)?.let(result::add)
                }
            }
        }

        result.addAll(replacement)
        return TextRow(
            spans = normalize(result, width),
            dirtyStart = start,
            dirtyEnd = end,
        )
    }

    fun cleared(): TextRow = TextRow(
        spans = if (columns == 0) emptyList() else listOf(TextSpan.blank(0, columns)),
        dirtyStart = 0,
        dirtyEnd = columns,
    )

    fun extract(startCol: Int, endCol: Int, targetStart: Int): List<TextSpan> {
        val from = startCol.coerceAtLeast(0)
        val to = endCol.coerceAtLeast(from)
        val result = ArrayList<TextSpan>()

        spans.forEach { span ->
            val overlapStart = maxOf(from, span.startCol)
            val overlapEnd = minOf(to, span.endCol)
            if (overlapStart < overlapEnd) {
                val slice = if (overlapStart == span.startCol && overlapEnd == span.endCol) {
                    span
                } else {
                    span.slice(overlapStart, overlapEnd)
                }

                if (slice != null) {
                    val target = targetStart + (overlapStart - from)
                    result.add(
                        if (slice.startCol == target) {
                            slice
                        } else {
                            TextSpan(
                                startCol = target,
                                columns = slice.columns,
                                text = slice.text,
                                hlId = slice.hlId,
                                wide = slice.wide,
                            )
                        },
                    )
                }
            }
        }

        return result
    }

    fun spanAt(column: Int): TextSpan? = spans.firstOrNull { column in it.startCol until it.endCol }

    fun spanEndingAt(column: Int): TextSpan? = spans.firstOrNull { it.endCol == column }

    companion object {
        fun blank(width: Int): TextRow {
            val columns = width.coerceAtLeast(0)
            return TextRow(
                spans = if (columns == 0) {
                    emptyList()
                } else {
                    listOf(TextSpan.blank(0, columns))
                },
                dirtyStart = 0,
                dirtyEnd = columns,
            )
        }

        private fun normalize(spans: List<TextSpan>, width: Int): List<TextSpan> {
            if (width <= 0) return emptyList()

            val sorted = spans.filter { it.columns > 0 }.sortedBy { it.startCol }
            val result = ArrayList<TextSpan>(sorted.size + 2)
            var expected = 0

            for (span in sorted) {
                if (span.endCol <= expected) continue

                var current = if (span.startCol < expected) {
                    span.slice(expected, span.endCol)
                } else {
                    span
                } ?: continue

                if (current.startCol > expected) {
                    result.add(TextSpan.blank(expected, current.startCol - expected))
                }

                val last = result.lastOrNull()
                if (
                    last != null &&
                    !last.wide &&
                    !current.wide &&
                    last.hlId == current.hlId &&
                    last.endCol == current.startCol
                ) {
                    result[result.lastIndex] = TextSpan(
                        startCol = last.startCol,
                        columns = last.columns + current.columns,
                        text = last.text + current.text,
                        hlId = last.hlId,
                    )
                    current = result.last()
                } else {
                    result.add(current)
                }

                expected = current.endCol
            }

            if (expected < width) {
                result.add(TextSpan.blank(expected, width - expected))
            }

            return result
        }
    }
}

@Immutable
data class NvimHighlight(
    val foreground: Color? = null,
    val background: Color? = null,
    val special: Color? = null,
    val reverse: Boolean = false,
    val italic: Boolean = false,
    val bold: Boolean = false,
    val strikethrough: Boolean = false,
    val underline: Boolean = false,
    val undercurl: Boolean = false,
    val underdouble: Boolean = false,
    val underdotted: Boolean = false,
    val underdashed: Boolean = false,
    val altfont: Boolean = false,
    val dim: Boolean = false,
    val blink: Boolean = false,
    val conceal: Boolean = false,
    val overline: Boolean = false,
    val blend: Int = 0,
    val url: String? = null,
) {
    val hasCustomDecoration: Boolean
        get() = underline || undercurl || underdouble || underdotted || underdashed || overline

    fun spanStyle(defaults: SpanStyle): SpanStyle {
        val (foreground, background) = resolveColors(defaults)
        return SpanStyle(
            color = foreground,
            background = background,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontFeatureSettings = if (altfont) "ss01" else null,
            textDecoration = if (strikethrough) {
                TextDecoration.LineThrough
            } else {
                TextDecoration.None
            },
        )
    }

    fun resolvedBackground(defaults: SpanStyle): Color = resolveColors(defaults).second

    fun resolvedSpecial(defaults: SpanStyle, defaultSpecial: Color): Color {
        if (special != null) return special

        val foreground = resolveColors(defaults).first
        return if (foreground == Color.Unspecified) defaultSpecial else foreground
    }

    private fun resolveColors(defaults: SpanStyle): Pair<Color, Color> {
        val defaultForeground = defaults.color.orElse(Color.White)
        val defaultBackground = defaults.background.orElse(Color.Black)

        var foreground = foreground ?: defaultForeground
        var background = background ?: defaultBackground
        if (reverse) {
            val swap = foreground
            foreground = background
            background = swap
        }

        val blendOpacity = 1f - blend.coerceIn(0, 100) / 100f
        if (blendOpacity < 1f) {
            foreground = foreground.copy(alpha = foreground.alpha * blendOpacity)
            background = background.copy(alpha = background.alpha * blendOpacity)
        }
        if (dim) {
            foreground = foreground.copy(alpha = foreground.alpha * DIM_OPACITY)
        }

        return foreground to background
    }

    companion object {
        val Default = NvimHighlight()

        private const val DIM_OPACITY = 0.55f
    }
}

private fun Color.orElse(fallback: Color): Color = if (this == Color.Unspecified) fallback else this

/**
 * An immutable container for render state
 */
@Immutable
class NvimFrame(
    val generation: Long = 0,
    val rows: List<TextRow> = emptyList(),
    val styles: Map<Int, NvimHighlight> = emptyMap(),
    val defaultColors: SpanStyle = SpanStyle(color = Color.White, background = Color.Black),
    val defaultSpecial: Color = Color.Unspecified,
    val cursor: CursorState = CursorState(),
    val modeInfo: List<ModeProperty> = emptyList(),
    val cursorStyleEnabled: Boolean = true,
    val mode: Int = 0,
    val options: Map<String, Any?> = emptyMap(),
    val isBusy: Boolean = false,
    val title: String? = null,
    val icon: String? = null,
    val cwd: String? = null,
    val hlGroups: Map<String, Int> = emptyMap(),
) {
    val cursorMode: ModeProperty?
        get() = if (cursorStyleEnabled) modeInfo.getOrNull(mode) else null

    fun highlightFor(hlId: Int): NvimHighlight = if (hlId == -1) {
        NvimHighlight.Default
    } else {
        styles[hlId] ?: NvimHighlight.Default
    }

    fun highlightForGroup(name: String): NvimHighlight? = hlGroups[name]?.let(::highlightFor)

    fun urlAt(rowIndex: Int, column: Int): String? {
        val row = rows.getOrNull(rowIndex) ?: return null
        val span = row.spanAt(column) ?: return null
        return highlightFor(span.hlId).url
    }

    override fun equals(other: Any?) = other is NvimFrame && other.generation == generation
    override fun hashCode() = generation.hashCode()
}

/**
 * Manages mutable nvim state
 */
class NvimStateActor {
    private var generation = 0L
    private var options: Map<String, Any?> = emptyMap()
    private var modeInfo: List<ModeProperty> = emptyList()
    private var cursorStyleEnabled: Boolean = true
    private var isBusy: Boolean = false
    private var modeIndex: Long = 0
    private var defaultColors: SpanStyle = SpanStyle(color = Color.White, background = Color.Black)
    private var defaultSpecial: Color = Color.Unspecified
    private var styles: Map<Int, NvimHighlight> = emptyMap()
    private var title: String? = null
    private var icon: String? = null
    private var cwd: String? = null
    private var hlGroups: Map<String, Int> = emptyMap()
    private var activeGrid: Long? = null
    private val rows: ArrayList<TextRow> = ArrayList()
    private var cursor: CursorState = CursorState()

    private val _state = MutableStateFlow(NvimFrame())
    val state = _state.asStateFlow()

    private fun publish(): NvimFrame {
        generation += 1
        return NvimFrame(
            generation = generation,
            rows = rows.toList(),
            styles = styles,
            defaultColors = defaultColors,
            defaultSpecial = defaultSpecial,
            cursor = cursor,
            modeInfo = modeInfo,
            cursorStyleEnabled = cursorStyleEnabled,
            mode = modeIndex.toInt(),
            options = options,
            isBusy = isBusy,
            title = title,
            icon = icon,
            cwd = cwd,
            hlGroups = hlGroups,
        )
    }

    fun apply(event: NvimUiEvent) {
        when (event) {
            is NvimUiEvent.SetTitle -> {
                title = event.title
            }

            is NvimUiEvent.SetIcon -> {
                icon = event.icon
            }

            is NvimUiEvent.Chdir -> {
                cwd = event.path
            }

            is NvimUiEvent.HlGroupSet -> {
                hlGroups = hlGroups + (event.name to event.id.toInt())
            }

            is NvimUiEvent.OptionSet -> {
                options = options + (event.name to event.value)
            }

            is NvimUiEvent.ModeInfoSet -> {
                cursorStyleEnabled = event.cursor_style_enabled
                modeInfo = event.mode_info
            }

            is NvimUiEvent.ModeChange -> {
                modeIndex = event.mode_idx
            }

            is NvimUiEvent.BusyStart -> {
                isBusy = true
            }

            is NvimUiEvent.BusyStop -> {
                isBusy = false
            }

            is NvimUiEvent.Flush -> {
                _state.value = publish()
            }

            is NvimUiEvent.DefaultColorSet -> {
                defaultColors = SpanStyle(
                    color = nvimRgbColor(event.rgb_fg),
                    background = nvimRgbColor(event.rgb_bg),
                )
                defaultSpecial = event.rgb_sp
                    .takeIf { it >= 0 }
                    ?.let(::nvimRgbColor)
                    ?: Color.Unspecified
            }

            is NvimUiEvent.HlAttrDefine -> {
                styles = styles + (event.id.toInt() to nvimAttrToHighlight(event.rgb_attr))
            }

            is NvimUiEvent.GridResize -> {
                activeGrid = event.grid
                rows.clear()
                repeat(event.height.toInt().coerceAtLeast(0)) {
                    rows.add(TextRow.blank(event.width.toInt()))
                }
            }

            is NvimUiEvent.GridLine -> applyGridLine(event)

            is NvimUiEvent.GridClear -> {
                for (i in rows.indices) rows[i] = rows[i].cleared()
            }

            is NvimUiEvent.GridDestroy -> {
                if (activeGrid == null || event.grid == activeGrid) {
                    rows.clear()
                    cursor = CursorState()
                    activeGrid = null
                }
            }

            is NvimUiEvent.GridCursorGoto -> {
                cursor = CursorState(event.row.toInt(), event.col.toInt())
            }

            is NvimUiEvent.GridScroll -> applyGridScroll(event)

            else -> Unit
        }
    }

    private fun applyGridLine(event: NvimUiEvent.GridLine) {
        val rowIndex = event.row.toInt()
        if (rowIndex !in rows.indices) return

        val row = rows[rowIndex]
        val startCol = event.col_start.toInt().coerceAtLeast(0)
        val replacement = ArrayList<TextSpan>(event.cells.size + 1)
        var replacementStart = Int.MAX_VALUE
        var lastHlId = -1
        var col = startCol

        fun add(span: TextSpan) {
            replacement.add(span)
            replacementStart = minOf(replacementStart, span.startCol)
        }

        event.cells.forEach { cell ->
            val hlId = (cell.hl_id ?: lastHlId).toInt()
            lastHlId = hlId

            val repeat = cell.repeat?.toInt() ?: 1
            if (repeat <= 0) return@forEach

            if (cell.text.isEmpty()) {
                val previous = replacement.lastOrNull()?.takeIf { it.endCol == col }
                if (previous != null) {
                    if (!previous.wide) {
                        replacement[replacement.lastIndex] = TextSpan(
                            startCol = previous.startCol,
                            columns = previous.columns + repeat,
                            text = previous.text,
                            hlId = previous.hlId,
                            wide = true,
                        )
                    }
                } else {
                    val existing = row.spanEndingAt(col)
                    if (existing != null) {
                        if (!existing.wide) {
                            splitLastCellForWide(existing)?.let { (prefix, wide) ->
                                prefix?.let(::add)
                                if (repeat == 1) {
                                    add(wide)
                                } else {
                                    add(
                                        TextSpan(
                                            startCol = wide.startCol,
                                            columns = wide.columns + repeat - 1,
                                            text = wide.text,
                                            hlId = wide.hlId,
                                            wide = true,
                                        ),
                                    )
                                }
                            }
                        }
                    } else if (row.spanAt(col) == null) {
                        add(TextSpan.blank(col, repeat))
                    }
                }

                col += repeat
                return@forEach
            }

            add(
                TextSpan(
                    startCol = col,
                    columns = repeat,
                    text = cell.text.repeat(repeat),
                    hlId = hlId,
                ),
            )
            col += repeat
        }

        if (replacement.isNotEmpty()) {
            rows[rowIndex] = row.patch(replacementStart, replacement)
        }
    }

    private fun applyGridScroll(event: NvimUiEvent.GridScroll) {
        val width = rows.firstOrNull()?.columns ?: return
        val top = event.top.toInt().coerceIn(0, rows.size)
        val bot = event.bot.toInt().coerceIn(top, rows.size)
        val rowDelta = event.rows.toInt()
        if (rowDelta == 0 || top >= bot) return

        val left = event.left.toInt().coerceIn(0, width)
        val right = event.right.toInt().coerceIn(left, width)
        if (left >= right) return

        if (left == 0 && right == width) {
            scrollFullWidth(top, bot, rowDelta, width)
        } else {
            scrollPartialWidth(top, bot, rowDelta, width, left, right)
        }
    }

    private fun scrollFullWidth(top: Int, bot: Int, rowDelta: Int, width: Int) {
        if (rowDelta > 0) {
            val count = rowDelta.coerceAtMost(bot - top)
            for (row in top until bot - count) {
                rows[row] = rows[row + count]
            }
            for (row in bot - count until bot) {
                rows[row] = TextRow.blank(width)
            }
        } else {
            val count = (-rowDelta).coerceAtMost(bot - top)
            for (row in bot - 1 downTo top + count) {
                rows[row] = rows[row - count]
            }
            for (row in top until top + count) {
                rows[row] = TextRow.blank(width)
            }
        }
    }

    private fun scrollPartialWidth(
        top: Int,
        bot: Int,
        rowDelta: Int,
        width: Int,
        left: Int,
        right: Int,
    ) {
        val blank = listOf(TextSpan.blank(left, right - left))

        if (rowDelta > 0) {
            val count = rowDelta.coerceAtMost(bot - top)
            for (row in top until bot - count) {
                val source = rows[row + count]
                rows[row] = rows[row].patch(left, source.extract(left, right, left))
            }
            for (row in bot - count until bot) {
                rows[row] = rows[row].patch(left, blank)
            }
        } else {
            val count = (-rowDelta).coerceAtMost(bot - top)
            for (row in bot - 1 downTo top + count) {
                val source = rows[row - count]
                rows[row] = rows[row].patch(left, source.extract(left, right, left))
            }
            for (row in top until top + count) {
                rows[row] = rows[row].patch(left, blank)
            }
        }
    }
}

private fun splitLastCellForWide(span: TextSpan): Pair<TextSpan?, TextSpan>? {
    if (span.wide) return null

    val lastStart = span.text.lastClusterStart()
    if (lastStart <= 0) {
        return null to TextSpan(
            startCol = span.startCol,
            columns = span.columns + 1,
            text = span.text,
            hlId = span.hlId,
            wide = true,
        )
    }

    val prefixColumns = (span.columns - 1).coerceAtLeast(0)
    val prefix = if (prefixColumns > 0) {
        TextSpan(
            startCol = span.startCol,
            columns = prefixColumns,
            text = span.text.substring(0, lastStart),
            hlId = span.hlId,
        )
    } else {
        null
    }
    val wide = TextSpan(
        startCol = span.startCol + prefixColumns,
        columns = span.columns - prefixColumns + 1,
        text = span.text.substring(lastStart),
        hlId = span.hlId,
        wide = true,
    )
    return prefix to wide
}

private fun String.lastClusterStart(): Int {
    if (isEmpty()) return 0

    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    iterator.last()
    val previous = iterator.previous()
    return if (previous == BreakIterator.DONE) 0 else previous
}

fun nvimRgbColor(rgb: Long): Color = Color(rgb.toInt() or 0xFF000000.toInt())

fun nvimAttrToHighlight(hl: HighlightAttr): NvimHighlight = NvimHighlight(
    foreground = hl.foreground?.let { nvimRgbColor(it) },
    background = hl.background?.let { nvimRgbColor(it) },
    special = hl.special?.let { nvimRgbColor(it) },
    reverse = hl.reverse,
    italic = hl.italic,
    bold = hl.bold,
    strikethrough = hl.strikethrough,
    underline = hl.underline,
    undercurl = hl.undercurl,
    underdouble = hl.underdouble,
    underdotted = hl.underdotted,
    underdashed = hl.underdashed,
    altfont = hl.altfont,
    dim = hl.dim,
    blink = hl.blink,
    conceal = hl.conceal,
    overline = hl.overline,
    blend = hl.blend?.toInt() ?: 0,
    url = hl.url,
)
