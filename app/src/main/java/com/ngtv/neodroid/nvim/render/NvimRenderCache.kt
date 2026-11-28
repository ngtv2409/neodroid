package com.ngtv.neodroid.nvim.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import com.ngtv.neodroid.nvim.NvimFrame
import com.ngtv.neodroid.nvim.NvimHighlight
import com.ngtv.neodroid.nvim.TextRow
import com.ngtv.neodroid.nvim.TextSpan
import com.ngtv.neodroid.perf.NvimPerf

internal enum class SegmentKind(val measureMetric: String, val drawMetric: String) {
    Content("render.measure", "render.draw"),
    Gutter("render.measure.gutter", "render.draw.gutter"),
}

@Immutable
internal class RenderedBackground(val startCol: Int, val columns: Int, val color: Color)

/**
 * Identity of a rendered text run.
 */
@Immutable
internal class SegmentKey(private val startCol: Int, private val spans: List<TextSpan>) {
    private val hash = spans.fold(startCol) { acc, span ->
        31 * acc + span.layoutHash
    }

    override fun hashCode() = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SegmentKey || startCol != other.startCol || hash != other.hash) {
            return false
        }
        if (spans.size != other.spans.size) return false

        for (index in spans.indices) {
            val left = spans[index]
            val right = other.spans[index]
            if (
                left.text != right.text ||
                left.hlId != right.hlId ||
                left.columns != right.columns ||
                left.wide != right.wide
            ) {
                return false
            }
        }

        return true
    }
}

@Immutable
internal class RenderedSegment(
    val key: SegmentKey,
    val startCol: Int,
    val columns: Int,
    val kind: SegmentKind,
    val wide: Boolean,
    val blink: Boolean,
    val layout: TextLayoutResult,
)

internal enum class DecorationKind {
    Underline,
    UnderDouble,
    UnderCurly,
    UnderDotted,
    UnderDashed,
    Overline,
}

@Immutable
internal class RenderedDecoration(
    val startCol: Int,
    val columns: Int,
    val kind: DecorationKind,
    val color: Color,
)

@Immutable
internal class RenderedRow(
    val segments: List<RenderedSegment>,
    val backgrounds: List<RenderedBackground>,
    val decorations: List<RenderedDecoration>,
)

/**
 * Builds the render model for a frame and owns all layout caches.
 */
internal class NvimRenderCache {
    private val rows = HashMap<TextRow, RenderedRow>()
    private val contentSegments = LayoutCache(
        maxEntries = MAX_CONTENT_SEGMENTS,
        initialCapacity = INITIAL_CONTENT_SEGMENTS,
    )
    private val gutterSegments = LayoutCache(
        maxEntries = MAX_GUTTER_SEGMENTS,
        initialCapacity = INITIAL_GUTTER_SEGMENTS,
    )
    private var cachedStyles: Map<Int, NvimHighlight>? = null
    private var cachedDefaultColors: SpanStyle? = null
    private var cachedDefaultSpecial: Color? = null
    private var resolvedStyles: Map<Int, SpanStyle>? = null

    fun render(
        frame: NvimFrame,
        maxRows: Int,
        textMeasurer: TextMeasurer,
        baseStyle: TextStyle,
    ): List<RenderedRow> = render(
        frame = frame,
        sourceRows = frame.rows,
        maxRows = maxRows,
        textMeasurer = textMeasurer,
        baseStyle = baseStyle,
    )

    fun render(
        frame: NvimFrame,
        sourceRows: List<TextRow>,
        maxRows: Int,
        textMeasurer: TextMeasurer,
        baseStyle: TextStyle,
    ): List<RenderedRow> {
        if (
            cachedStyles != frame.styles ||
            cachedDefaultColors != frame.defaultColors ||
            cachedDefaultSpecial != frame.defaultSpecial
        ) {
            rows.clear()
            contentSegments.clear()
            gutterSegments.clear()
            cachedStyles = frame.styles
            cachedDefaultColors = frame.defaultColors
            cachedDefaultSpecial = frame.defaultSpecial
            resolvedStyles = frame.styles.mapValues { (_, highlight) ->
                highlight.spanStyle(frame.defaultColors)
            }
        }

        val count = minOf(sourceRows.size, maxRows.coerceAtLeast(0))
        val result = ArrayList<RenderedRow>(count)

        for (index in 0 until count) {
            val row = sourceRows[index]
            val rendered = rows[row] ?: buildRow(
                row = row,
                frame = frame,
                textMeasurer = textMeasurer,
                baseStyle = baseStyle,
            ).also { rows[row] = it }

            result.add(rendered)
        }

        evictRows(frame)
        return result
    }

    private fun evictRows(frame: NvimFrame) {
        val liveRows = HashSet<TextRow>()
        liveRows.addAll(frame.rows)
        frame.layers.forEach { liveRows.addAll(it.rows) }
        val limit = liveRows.size * 2 + 64
        if (rows.size <= limit) return
        rows.keys.retainAll(liveRows)
    }

    private fun buildRow(
        row: TextRow,
        frame: NvimFrame,
        textMeasurer: TextMeasurer,
        baseStyle: TextStyle,
    ): RenderedRow {
        val backgrounds = buildBackgrounds(row, frame)
        val decorations = buildDecorations(row, frame)
        val gutterEnd = gutterEndCol(row)
        val segments = ArrayList<RenderedSegment>(4)

        for (range in segmentRanges(row, gutterEnd, frame)) {
            val kind = if (range.last < gutterEnd) {
                SegmentKind.Gutter
            } else {
                SegmentKind.Content
            }

            addSegment(
                target = segments,
                row = row,
                frame = frame,
                textMeasurer = textMeasurer,
                baseStyle = baseStyle,
                startCol = range.first,
                endCol = range.last + 1,
                kind = kind,
            )
        }

        return RenderedRow(
            segments = segments,
            backgrounds = backgrounds,
            decorations = decorations,
        )
    }

    private fun buildBackgrounds(row: TextRow, frame: NvimFrame): List<RenderedBackground> {
        val result = ArrayList<RenderedBackground>(row.spans.size)
        val defaultBackground = frame.defaultColors.background

        row.spans.forEach { span ->
            val background = highlightFor(span.hlId, frame)
                .resolvedBackground(frame.defaultColors)
            addBackground(
                target = result,
                startCol = span.startCol,
                columns = span.columns,
                color = background,
                defaultBackground = defaultBackground,
            )
        }

        return result
    }

    private fun buildDecorations(row: TextRow, frame: NvimFrame): List<RenderedDecoration> {
        val result = ArrayList<RenderedDecoration>()

        row.spans.forEach { span ->
            val highlight = highlightFor(span.hlId, frame)
            if (!highlight.hasCustomDecoration) return@forEach

            val color = highlight.resolvedSpecial(frame.defaultColors, frame.defaultSpecial)
            addDecoration(
                target = result,
                startCol = span.startCol,
                columns = span.columns,
                kind = DecorationKind.Underline,
                color = color,
                enabled = highlight.underline,
            )
            addDecoration(
                target = result,
                startCol = span.startCol,
                columns = span.columns,
                kind = DecorationKind.UnderCurly,
                color = color,
                enabled = highlight.undercurl,
            )
            addDecoration(
                target = result,
                startCol = span.startCol,
                columns = span.columns,
                kind = DecorationKind.UnderDouble,
                color = color,
                enabled = highlight.underdouble,
            )
            addDecoration(
                target = result,
                startCol = span.startCol,
                columns = span.columns,
                kind = DecorationKind.UnderDotted,
                color = color,
                enabled = highlight.underdotted,
            )
            addDecoration(
                target = result,
                startCol = span.startCol,
                columns = span.columns,
                kind = DecorationKind.UnderDashed,
                color = color,
                enabled = highlight.underdashed,
            )
            addDecoration(
                target = result,
                startCol = span.startCol,
                columns = span.columns,
                kind = DecorationKind.Overline,
                color = color,
                enabled = highlight.overline,
            )
        }

        return result
    }

    /**
     * Splits a row into cacheable ranges.
     *
     * The row is cut at the gutter boundary and at the last dirty range. The
     * dirty cuts keep unchanged text outside a patch reusable even when
     * normalized spans merge back into one highlighted run.
     */
    private fun segmentRanges(row: TextRow, gutterEnd: Int, frame: NvimFrame): List<IntRange> {
        val width = row.columns
        if (width <= 0) return emptyList()

        val cuts = sortedSetOf<Int>()
        cuts.add(0)
        if (gutterEnd in 1 until width) cuts.add(gutterEnd)

        val dirtyStart = row.dirtyStart.coerceIn(0, width)
        val dirtyEnd = row.dirtyEnd.coerceIn(dirtyStart, width)
        if (row.dirtyStart >= 0 && dirtyEnd > dirtyStart) {
            cuts.add(dirtyStart)
            cuts.add(dirtyEnd)
        }

        row.spans.forEach { span ->
            if (highlightFor(span.hlId, frame).blink) {
                cuts.add(span.startCol)
                cuts.add(span.endCol)
            }
        }

        val wideSpans = row.spans.filter { it.wide }
        wideSpans.forEach { span ->
            cuts.add(span.startCol)
            cuts.add(span.endCol)
        }
        if (wideSpans.isNotEmpty()) {
            cuts.removeAll(
                cuts.filter { cut ->
                    wideSpans.any { cut > it.startCol && cut < it.endCol }
                },
            )
        }

        cuts.add(width)

        val ranges = ArrayList<IntRange>(cuts.size - 1)
        var start = cuts.first()
        for (end in cuts) {
            if (end > start) ranges.add(start until end)
            start = end
        }
        return ranges
    }

    private fun addSegment(
        target: MutableList<RenderedSegment>,
        row: TextRow,
        frame: NvimFrame,
        textMeasurer: TextMeasurer,
        baseStyle: TextStyle,
        startCol: Int,
        endCol: Int,
        kind: SegmentKind,
    ) {
        if (startCol >= endCol) return

        val spans = row.extract(startCol, endCol, startCol)
        if (spans.isEmpty()) return

        val key = SegmentKey(startCol, spans)
        val cache = if (kind == SegmentKind.Gutter) gutterSegments else contentSegments
        val segment = cache[key] ?: RenderedSegment(
            key = key,
            startCol = startCol,
            columns = endCol - startCol,
            kind = kind,
            wide = spans.any { it.wide },
            blink = spans.any { highlightFor(it.hlId, frame).blink },
            layout = NvimPerf.measure(kind.measureMetric) {
                measureSegment(spans, frame, textMeasurer, baseStyle)
            },
        ).also { cache[key] = it }

        target.add(segment)
    }

    /**
     * ext_linegrid does not expose the line-number gutter separately. Infer the
     * leading digit/whitespace prefix so relative-number changes do not
     * invalidate the content segment after it.
     */
    private fun gutterEndCol(row: TextRow): Int {
        row.spans.forEach { span ->
            if (span.wide) return span.startCol.coerceIn(0, row.columns)

            span.text.forEachIndexed { index, char ->
                if (!char.isDigit() && !char.isWhitespace()) {
                    return (span.startCol + index).coerceIn(0, row.columns)
                }
            }
        }

        return row.columns
    }

    private fun measureSegment(
        spans: List<TextSpan>,
        frame: NvimFrame,
        textMeasurer: TextMeasurer,
        baseStyle: TextStyle,
    ): TextLayoutResult {
        val text = AnnotatedString.Builder()

        spans.forEach { span ->
            val highlight = highlightFor(span.hlId, frame)
            val style = styleFor(span.hlId, frame)
            val start = text.length
            if (highlight.conceal) {
                text.append(" ".repeat(span.columns))
            } else {
                text.append(span.text)
            }
            if (text.length > start) {
                text.addStyle(
                    style.copy(background = Color.Unspecified),
                    start,
                    text.length,
                )
            }
        }

        return textMeasurer.measure(
            text = text.toAnnotatedString(),
            style = baseStyle,
            softWrap = false,
            maxLines = 1,
        )
    }

    private fun highlightFor(hlId: Int, frame: NvimFrame): NvimHighlight = frame.highlightFor(hlId)

    private fun styleFor(hlId: Int, frame: NvimFrame): SpanStyle = if (hlId == -1) {
        frame.defaultColors
    } else {
        resolvedStyles?.get(hlId) ?: frame.defaultColors
    }

    private fun addDecoration(
        target: MutableList<RenderedDecoration>,
        startCol: Int,
        columns: Int,
        kind: DecorationKind,
        color: Color,
        enabled: Boolean,
    ) {
        if (!enabled || columns <= 0 || color == Color.Unspecified) return

        val previous = target.lastOrNull()
        if (
            previous != null &&
            previous.kind == kind &&
            previous.color == color &&
            previous.startCol + previous.columns == startCol
        ) {
            target[target.lastIndex] = RenderedDecoration(
                startCol = previous.startCol,
                columns = previous.columns + columns,
                kind = kind,
                color = color,
            )
        } else {
            target.add(RenderedDecoration(startCol, columns, kind, color))
        }
    }

    private fun addBackground(
        target: MutableList<RenderedBackground>,
        startCol: Int,
        columns: Int,
        color: Color,
        defaultBackground: Color,
    ) {
        if (columns <= 0 || color == Color.Unspecified || color == defaultBackground) {
            return
        }

        val previous = target.lastOrNull()
        if (
            previous != null &&
            previous.color == color &&
            previous.startCol + previous.columns == startCol
        ) {
            target[target.lastIndex] = RenderedBackground(
                startCol = previous.startCol,
                columns = previous.columns + columns,
                color = color,
            )
        } else {
            target.add(RenderedBackground(startCol, columns, color))
        }
    }

    private class LayoutCache(private val maxEntries: Int, initialCapacity: Int) {
        private val entries = object : LinkedHashMap<SegmentKey, RenderedSegment>(
            initialCapacity,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<SegmentKey, RenderedSegment>?,
            ): Boolean = size > maxEntries
        }

        operator fun get(key: SegmentKey): RenderedSegment? = entries[key]

        operator fun set(key: SegmentKey, value: RenderedSegment) {
            entries[key] = value
        }

        fun clear() = entries.clear()
    }

    private companion object {
        const val MAX_CONTENT_SEGMENTS = 2048
        const val MAX_GUTTER_SEGMENTS = 512
        const val INITIAL_CONTENT_SEGMENTS = 256
        const val INITIAL_GUTTER_SEGMENTS = 64
    }
}
