package com.ngtv.neodroid.nvim

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
import java.util.ArrayList

data class CursorState(val row: Int = 0, val col: Int = 0)

/**
 * A collection of text with the same hl group
 */
class TextSpan(
    val startCol: Int,
    val columns: Int,
    val text: String,
    val hlId: Int,
    val revision: Long,
) {
    val endCol: Int
        get() = startCol + columns

    /**
     * Extracts columns [start, end) from this span.
     *
     * Neovim cells are one column per grid cell. The text is assumed to have one
     * character per column; wide characters ignored (for now)
     */
    fun slice(start: Int, end: Int): TextSpan? {
        val from = maxOf(start, startCol)
        val to = minOf(end, endCol)
        if (from >= to) return null

        val fromOffset = (from - startCol).coerceIn(0, text.length)
        val toOffset = (to - startCol).coerceIn(fromOffset, text.length)

        return TextSpan(
            startCol = from,
            columns = to - from,
            text = text.substring(fromOffset, toOffset),
            hlId = hlId,
            revision = revision,
        )
    }

    companion object {
        fun blank(startCol: Int, columns: Int): TextSpan = TextSpan(
            startCol = startCol,
            columns = columns,
            text = " ".repeat(columns),
            hlId = -1,
            revision = 0,
        )
    }
}

class TextRow(val revision: Long, val spans: List<TextSpan>) {
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
        return TextRow(revision + 1, normalize(result, width))
    }

    fun cleared(): TextRow = TextRow(
        revision + 1,
        if (columns == 0) emptyList() else listOf(TextSpan.blank(0, columns)),
    )

    fun extract(startCol: Int, endCol: Int, targetStart: Int): List<TextSpan> {
        val from = startCol.coerceAtLeast(0)
        val to = endCol.coerceAtLeast(from)
        val result = ArrayList<TextSpan>()

        spans.forEach { span ->
            val overlapStart = maxOf(from, span.startCol)
            val overlapEnd = minOf(to, span.endCol)
            if (overlapStart < overlapEnd) {
                span.slice(overlapStart, overlapEnd)?.let { slice ->
                    result.add(
                        TextSpan(
                            startCol = targetStart + (overlapStart - from),
                            columns = slice.columns,
                            text = slice.text,
                            hlId = slice.hlId,
                            revision = slice.revision,
                        ),
                    )
                }
            }
        }

        return result
    }

    companion object {
        fun blank(width: Int): TextRow {
            val columns = width.coerceAtLeast(0)
            return TextRow(
                revision = 0,
                spans = if (columns == 0) {
                    emptyList()
                } else {
                    listOf(TextSpan.blank(0, columns))
                },
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
                if (last != null && last.hlId == current.hlId && last.endCol == current.startCol) {
                    result[result.lastIndex] = TextSpan(
                        startCol = last.startCol,
                        columns = last.columns + current.columns,
                        text = last.text + current.text,
                        hlId = last.hlId,
                        revision = maxOf(last.revision, current.revision),
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

/**
 * An immutable container for render state
 */
class NvimFrame(
    val generation: Long = 0,
    val rows: List<TextRow> = emptyList(),
    val styles: Map<Int, SpanStyle> = emptyMap(),
    val defaultColors: SpanStyle = SpanStyle(color = Color.White, background = Color.Black),
    val cursor: CursorState = CursorState(),
    val modeInfo: List<ModeProperty> = emptyList(),
    val cursorStyleEnabled: Boolean = true,
    val mode: Int = 0,
    val options: Map<String, Any?> = emptyMap(),
    val isBusy: Boolean = true,
) {
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
    private var isBusy: Boolean = true
    private var modeIndex: Long = 0
    private var defaultColors: SpanStyle = SpanStyle(color = Color.White, background = Color.Black)
    private var styles: Map<Int, SpanStyle> = emptyMap()
    private val rows: ArrayList<TextRow> = ArrayList()
    private var cursor: CursorState = CursorState()
    private var spanRevision = 0L

    private val _state = MutableStateFlow(NvimFrame())
    val state = _state.asStateFlow()

    internal fun styleFor(hlId: Int): SpanStyle {
        if (hlId == -1) return defaultColors
        return styles[hlId] ?: defaultColors
    }

    private fun publish(): NvimFrame {
        generation += 1
        return NvimFrame(
            generation = generation,
            rows = rows.toList(),
            styles = styles,
            defaultColors = defaultColors,
            cursor = cursor,
            modeInfo = modeInfo,
            cursorStyleEnabled = cursorStyleEnabled,
            mode = modeIndex.toInt(),
            options = options,
            isBusy = isBusy,
        )
    }

    fun apply(event: NvimUiEvent) {
        when (event) {
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
            }

            is NvimUiEvent.HlAttrDefine -> {
                styles = styles + (event.id.toInt() to nvimAttrToStyle(event.rgb_attr))
            }

            is NvimUiEvent.GridResize -> {
                rows.clear()
                repeat(event.height.toInt().coerceAtLeast(0)) {
                    rows.add(TextRow.blank(event.width.toInt()))
                }
            }

            is NvimUiEvent.GridLine -> applyGridLine(event)

            is NvimUiEvent.GridClear -> {
                for (i in rows.indices) rows[i] = rows[i].cleared()
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

        val startCol = event.col_start.toInt().coerceAtLeast(0)
        val replacement = ArrayList<TextSpan>(event.cells.size)
        var lastHlId = -1
        var col = startCol

        event.cells.forEach { cell ->
            val hlId = (cell.hl_id ?: lastHlId).toInt()
            lastHlId = hlId

            val repeat = cell.repeat?.toInt() ?: 1
            if (repeat <= 0) return@forEach

            replacement.add(
                TextSpan(
                    startCol = col,
                    columns = repeat,
                    text = cell.text.repeat(repeat),
                    hlId = hlId,
                    revision = ++spanRevision,
                ),
            )
            col += repeat
        }

        if (replacement.isNotEmpty()) {
            rows[rowIndex] = rows[rowIndex].patch(startCol, replacement)
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

fun NvimStateActor.hlIdToStyle(hlId: Int): SpanStyle = styleFor(hlId)

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
