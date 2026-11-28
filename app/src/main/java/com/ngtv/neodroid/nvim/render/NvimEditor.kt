package com.ngtv.neodroid.nvim.render

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngtv.neodroid.core.events.CursorShape
import com.ngtv.neodroid.core.events.ModeProperty
import com.ngtv.neodroid.nvim.CursorState
import com.ngtv.neodroid.nvim.NvimFrame
import com.ngtv.neodroid.nvim.NvimGridLayer
import com.ngtv.neodroid.nvim.NvimHighlight
import com.ngtv.neodroid.nvim.NvimLayerKind
import com.ngtv.neodroid.nvim.TextRow
import com.ngtv.neodroid.nvim.input.NvimGestureView
import com.ngtv.neodroid.nvim.input.NvimGridHit
import com.ngtv.neodroid.nvim.input.NvimMouseEvent
import com.ngtv.neodroid.perf.NvimPerf
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NvimEditor(
    frame: NvimFrame,
    onResize: suspend (width: Int, height: Int) -> Unit,
    onMouse: (NvimMouseEvent) -> Unit,
    takeModifiers: () -> String,
    onPaste: (String) -> Unit = {},
    onMessage: (String) -> Unit = {},
    selectionMode: Boolean = false,
    onSelectionFinished: () -> Unit = {},
    onOpenUrlError: (String) -> Unit = {},
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    smoothScroll: Boolean = true,
    scrollAnimationLengthMs: Int = 300,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val context = LocalContext.current

    var selection by remember { mutableStateOf<GridSelection?>(null) }
    var menu by remember { mutableStateOf<GridMenu?>(null) }

    LaunchedEffect(selectionMode) {
        if (!selectionMode) {
            selection = null
            menu = null
        }
    }

    val clipboard = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }

    fun finishSelectionMode() {
        if (selectionMode) onSelectionFinished()
    }

    fun copySelection() {
        val selected = selection ?: return
        val text = frame.selectedText(selected)
        if (text.isEmpty()) {
            onMessage("Nothing to copy")
        } else {
            val copied = clipboard != null && runCatching {
                clipboard.setPrimaryClip(ClipData.newPlainText("Neovim", text))
            }.isSuccess
            if (copied) {
                val lines = text.count { it == '\n' } + 1
                onMessage(if (lines == 1) "Copied line" else "Copied $lines lines")
            } else {
                onMessage("Could not copy to clipboard")
            }
        }
        selection = null
        menu = null
        finishSelectionMode()
    }

    fun pasteFromClipboard() {
        val text = runCatching {
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0)?.coerceToText(context)?.toString()
            } else {
                null
            }
        }.getOrNull()

        if (text.isNullOrEmpty()) {
            onMessage("Clipboard is empty")
        } else {
            onPaste(text)
        }
        selection = null
        menu = null
        finishSelectionMode()
    }

    fun selectAll(columns: Int) {
        val primaryRows = frame.primaryRows
        val lastRow = primaryRows.lastIndex
        if (lastRow < 0) return
        val lastColumn = (
            frame.primaryLayer?.width
                ?: primaryRows.maxOfOrNull { it.columns }
                ?: columns
            )
            .coerceAtLeast(1)
            .minus(1)
        selection = GridSelection(
            anchor = GridCell(0, 0),
            focus = GridCell(lastRow, lastColumn),
        )
        menu = GridMenu(
            kind = GridMenuKind.Selection,
            cell = GridCell(lastRow, lastColumn),
        )
    }

    val baseTextStyle = remember(fontFamily, fontSize, lineHeight) {
        TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
        )
    }

    val cellMetrics = remember(textMeasurer, baseTextStyle, density) {
        textMeasurer.measure(
            text = AnnotatedString("M"),
            style = baseTextStyle,
        ).size
    }

    val cellWidth = with(density) { cellMetrics.width.toDp() }
    val cellHeight = with(density) { cellMetrics.height.toDp() }

    val renderCache = remember(textMeasurer, baseTextStyle, density) {
        NvimRenderCache()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(frame.defaultColors.background),
    ) {
        val columns = (maxWidth / cellWidth).toInt().coerceAtLeast(1)
        val visibleRows = (maxHeight / cellHeight).toInt().coerceAtLeast(1)

        LaunchedEffect(columns, visibleRows) {
            // Debounce while the IME is animating to avoid resize churn.
            delay(RESIZE_DEBOUNCE_MS)
            onResize(columns, visibleRows)
        }

        val layers = if (frame.layers.any { it.rows.isNotEmpty() }) {
            frame.layers
        } else if (frame.rows.isNotEmpty()) {
            listOf(
                NvimGridLayer(
                    grid = 1L,
                    window = null,
                    rows = frame.rows,
                    startRow = 0,
                    startCol = 0,
                    width = frame.rows.maxOfOrNull { it.columns } ?: 0,
                    zOrder = 0,
                    kind = NvimLayerKind.Global,
                ),
            )
        } else {
            emptyList()
        }

        if (layers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Waiting for Neovim UI...")
            }
        } else {
            // nvim grid can be too big and extend beyond visible screen,
            // so if when is lowering, compose size change quickly and may reveal stale content
            // clamp them
            val screenRows = layers
                .firstOrNull { it.grid == GLOBAL_GRID && it.rows.isNotEmpty() }
                ?.height
                ?.takeIf { it > 0 }
                ?: layers.maxOfOrNull { it.startRow + it.height }
                    ?.takeIf { it > 0 }
                ?: visibleRows
            val blinkVisible = rememberTextBlinkVisible(frame)
            val uriHandler = LocalUriHandler.current
            val primaryLayer = frame.primaryLayer

            Box(
                modifier = Modifier
                    .width(cellWidth * columns.toFloat())
                    .height(cellHeight * visibleRows.toFloat())
                    .clipToBounds(),
            ) {
                layers.forEach { layer ->
                    key(layer.grid) {
                        NvimLayerView(
                            layer = layer,
                            frame = frame,
                            visibleRows = visibleRows,
                            screenRows = screenRows,
                            renderCache = renderCache,
                            cellWidth = cellWidth,
                            cellHeight = cellHeight,
                            blinkVisible = blinkVisible,
                            textMeasurer = textMeasurer,
                            baseTextStyle = baseTextStyle,
                            smoothScroll = smoothScroll,
                            scrollAnimationLengthMs = scrollAnimationLengthMs,
                        )
                    }
                }
                NvimSelectionOverlay(
                    selection = selection,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    columns = primaryLayer?.width?.coerceAtLeast(1) ?: columns,
                )
                NvimGestureOverlay(
                    cellWidthPx = with(density) { cellWidth.toPx() },
                    cellHeightPx = with(density) { cellHeight.toPx() },
                    columns = columns,
                    rows = visibleRows,
                    hasSelection = selection != null,
                    selectionMode = selectionMode,
                    hitTest = frame::hitAt,
                    onOpenUrl = { url ->
                        runCatching { uriHandler.openUri(url) }.onFailure {
                            onOpenUrlError("Could not open link: $url")
                        }
                    },
                    onMouse = onMouse,
                    takeModifiers = takeModifiers,
                    onContextMenu = { row, col ->
                        selection = null
                        menu = GridMenu(
                            kind = GridMenuKind.Context,
                            cell = GridCell(row, col),
                        )
                    },
                    onSelectionChange = { anchorRow, anchorCol, focusRow, focusCol ->
                        menu = null
                        selection = GridSelection(
                            anchor = GridCell(anchorRow, anchorCol),
                            focus = GridCell(focusRow, focusCol),
                        )
                    },
                    onSelectionEnd = { anchorRow, anchorCol, focusRow, focusCol ->
                        selection = GridSelection(
                            anchor = GridCell(anchorRow, anchorCol),
                            focus = GridCell(focusRow, focusCol),
                        )
                        menu = GridMenu(
                            kind = GridMenuKind.Selection,
                            cell = GridCell(focusRow, focusCol),
                        )
                    },
                    onClearSelection = {
                        selection = null
                        menu = null
                        finishSelectionMode()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                NvimGridMenu(
                    menu = menu,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    columns = columns,
                    rows = visibleRows,
                    onDismiss = {
                        menu = null
                        selection = null
                        finishSelectionMode()
                    },
                    onPaste = ::pasteFromClipboard,
                    onCopy = ::copySelection,
                    onSelectAll = { selectAll(columns) },
                )
            }
        }
    }
}

private data class GridCell(val row: Int, val col: Int)

private data class GridSelection(val anchor: GridCell, val focus: GridCell) {
    fun normalized(): GridSelection {
        val anchorFirst = anchor.row < focus.row ||
            (anchor.row == focus.row && anchor.col <= focus.col)
        return if (anchorFirst) this else GridSelection(anchor = focus, focus = anchor)
    }
}

private enum class GridMenuKind { Context, Selection }

private data class GridMenu(val kind: GridMenuKind, val cell: GridCell)

private fun NvimFrame.selectedText(selection: GridSelection): String {
    val selectedRows = primaryRows
    val lastRow = selectedRows.lastIndex
    if (lastRow < 0) return ""

    val normalized = selection.normalized()
    val startRow = normalized.anchor.row.coerceIn(0, lastRow)
    val endRow = normalized.focus.row.coerceIn(startRow, lastRow)
    val builder = StringBuilder()

    for (rowIndex in startRow..endRow) {
        if (rowIndex > startRow) builder.append('\n')

        val row = selectedRows.getOrNull(rowIndex) ?: continue
        val rowColumns = row.columns
        val from = if (rowIndex == startRow) {
            normalized.anchor.col.coerceIn(0, rowColumns)
        } else {
            0
        }
        val to = if (rowIndex == endRow) {
            (normalized.focus.col + 1).coerceIn(0, rowColumns)
        } else {
            rowColumns
        }

        if (to > from) {
            builder.append(row.textBetween(from, to).trimEnd())
        }
    }

    return builder.toString()
}

@Composable
private fun NvimSelectionOverlay(
    selection: GridSelection?,
    cellWidth: Dp,
    cellHeight: Dp,
    columns: Int,
) {
    if (selection == null) return

    val normalized = selection.normalized()
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = cellWidth.toPx()
        val height = cellHeight.toPx()
        val startRow = normalized.anchor.row.coerceAtLeast(0)
        val endRow = normalized.focus.row.coerceAtLeast(startRow)

        for (row in startRow..endRow) {
            val startCol = if (row == startRow) {
                normalized.anchor.col.coerceIn(0, columns - 1)
            } else {
                0
            }
            val endCol = if (row == endRow) {
                (normalized.focus.col + 1).coerceIn(startCol + 1, columns)
            } else {
                columns
            }
            drawRect(
                color = color,
                topLeft = Offset(startCol * width, row * height),
                size = Size((endCol - startCol) * width, height),
            )
        }
    }
}

@Composable
private fun NvimGridMenu(
    menu: GridMenu?,
    cellWidth: Dp,
    cellHeight: Dp,
    columns: Int,
    rows: Int,
    onDismiss: () -> Unit,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val current = menu ?: return
    val column = current.cell.col.coerceIn(0, (columns - 1).coerceAtLeast(0))
    val row = current.cell.row.coerceIn(0, (rows - 1).coerceAtLeast(0))

    Box(
        modifier = Modifier.offset(
            x = cellWidth * column,
            y = cellHeight * row,
        ),
    ) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismiss,
        ) {
            when (current.kind) {
                GridMenuKind.Context -> {
                    DropdownMenuItem(
                        text = { Text("Paste") },
                        onClick = onPaste,
                    )
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        onClick = onSelectAll,
                    )
                }

                GridMenuKind.Selection -> {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = onCopy,
                    )
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        onClick = onSelectAll,
                    )
                    DropdownMenuItem(
                        text = { Text("Paste") },
                        onClick = onPaste,
                    )
                }
            }
        }
    }
}

@Composable
private fun NvimLayerView(
    layer: NvimGridLayer,
    frame: NvimFrame,
    visibleRows: Int,
    screenRows: Int,
    renderCache: NvimRenderCache,
    cellWidth: Dp,
    cellHeight: Dp,
    blinkVisible: Boolean,
    textMeasurer: TextMeasurer,
    baseTextStyle: TextStyle,
    smoothScroll: Boolean,
    scrollAnimationLengthMs: Int,
) {
    if (layer.rows.isEmpty() || layer.width <= 0) return

    val scrollPosition = rememberSmoothScroll(
        layer = layer,
        enabled = smoothScroll && layer.margins.top == 0 && layer.margins.bottom == 0,
        animationLengthMs = scrollAnimationLengthMs,
    )
    val scrollBase by remember(scrollPosition) {
        derivedStateOf { floor(scrollPosition.value).toInt() }
    }
    val cellHeightPx = with(LocalDensity.current) { cellHeight.toPx() }

    val rows = remember(
        frame,
        layer,
        scrollBase,
        visibleRows,
        screenRows,
        renderCache,
        textMeasurer,
        baseTextStyle,
    ) {
        val buffer = layer.scrollRows
        val source = if (buffer.isNotEmpty()) buffer else layer.rows
        val origin = if (buffer.isNotEmpty()) layer.scrollOrigin else 0
        val start = (origin + scrollBase).coerceIn(0, (source.size - 1).coerceAtLeast(0))
        val layerRows = minOf(
            layer.height,
            visibleRows,
            (screenRows - layer.startRow).coerceAtLeast(0),
        )
        val maxRows = if (layerRows == 0) 0 else layerRows + SCROLL_OVERDRAW_ROWS
        val count = minOf(maxRows, source.size - start).coerceAtLeast(0)

        NvimPerf.measure("render.compose") {
            renderCache.render(
                frame = frame,
                sourceRows = source.subList(start, start + count),
                maxRows = count,
                textMeasurer = textMeasurer,
                baseStyle = baseTextStyle,
            )
        }
    }

    val viewportRows = minOf(
        layer.height,
        visibleRows,
        (screenRows - layer.startRow).coerceAtLeast(0),
    ).coerceAtLeast(1)
    Box(
        modifier = Modifier
            .offset(
                x = cellWidth * layer.startCol.toFloat(),
                y = cellHeight * layer.startRow.toFloat(),
            )
            .width(cellWidth * layer.width.toFloat())
            .height(cellHeight * viewportRows.toFloat())
            .background(frame.defaultColors.background)
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .width(cellWidth * layer.width.toFloat())
                .height(cellHeight * rows.size.toFloat())
                .graphicsLayer {
                    val position = scrollPosition.value
                    translationY = (floor(position) - position) * cellHeightPx
                },
        ) {
            NvimGrid(
                rows = rows,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                blinkVisible = blinkVisible,
            )
        }
        layer.cursor?.let { cursor ->
            NvimCursorOverlay(
                frame = frame,
                rows = layer.rows,
                cursor = cursor,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                textMeasurer = textMeasurer,
                baseTextStyle = baseTextStyle,
                isScrolling = scrollPosition.value != 0f,
            )
        }
    }
}

/**
 * Animates the grid offset when scrolling as a damped string
 */
@Composable
private fun rememberSmoothScroll(
    layer: NvimGridLayer,
    enabled: Boolean,
    animationLengthMs: Int,
): State<Float> {
    val position = remember { Animatable(0f) }
    var committedGeneration by remember { mutableLongStateOf(layer.scrollGeneration) }
    var committedTotal by remember { mutableLongStateOf(layer.scrollTotal) }
    val generation = layer.scrollGeneration
    val scrollTotal = layer.scrollTotal
    val margin = layer.scrollMargin.coerceAtLeast(1)
    val animationSpec = remember(animationLengthMs) {
        val seconds = animationLengthMs.coerceAtLeast(1) / 1000f
        val stiffness = (4f / seconds).let { it * it }
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = stiffness,
            visibilityThreshold = 0.01f,
        )
    }

    LaunchedEffect(generation, enabled) {
        if (committedGeneration == generation) {
            position.snapTo(0f)
            return@LaunchedEffect
        }
        if (!enabled) {
            position.snapTo(0f)
            committedGeneration = generation
            committedTotal = scrollTotal
            return@LaunchedEffect
        }

        val pending = (scrollTotal - committedTotal).toFloat()
        if (pending != 0f) {
            val next = (position.value - pending).coerceIn(-margin.toFloat(), margin.toFloat())
            position.snapTo(next)
        }
        committedGeneration = generation
        committedTotal = scrollTotal
        position.animateTo(
            targetValue = 0f,
            animationSpec = animationSpec,
        )
    }

    return remember(
        generation,
        scrollTotal,
        committedGeneration,
        committedTotal,
        margin,
        position,
    ) {
        derivedStateOf {
            val pending = if (committedGeneration != generation) {
                (scrollTotal - committedTotal).toFloat()
            } else {
                0f
            }
            (position.value - pending).coerceIn(-margin.toFloat(), margin.toFloat())
        }
    }
}

@Composable
private fun NvimGrid(
    rows: List<RenderedRow>,
    cellWidth: Dp,
    cellHeight: Dp,
    blinkVisible: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = cellWidth.toPx()
        val height = cellHeight.toPx()

        rows.forEachIndexed { rowIndex, row ->
            row.backgrounds.forEach { background ->
                drawRect(
                    color = background.color,
                    topLeft = Offset(
                        x = background.startCol * width,
                        y = rowIndex * height,
                    ),
                    size = Size(
                        width = background.columns * width,
                        height = height,
                    ),
                )
            }
        }
    }

    val occurrences = HashMap<SegmentKey, Int>()

    rows.forEachIndexed { rowIndex, row ->
        row.segments.forEach { segment ->
            val occurrence = occurrences.getOrDefault(segment.key, 0)
            occurrences[segment.key] = occurrence + 1

            key(segment.key, occurrence) {
                NvimTextSegment(
                    segment = segment,
                    rowIndex = rowIndex,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    blinkVisible = blinkVisible,
                )
            }
        }
    }

    if (rows.any { it.decorations.isNotEmpty() }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = cellWidth.toPx()
            val height = cellHeight.toPx()

            NvimPerf.measure("render.draw.decorations") {
                rows.forEachIndexed { rowIndex, row ->
                    row.decorations.forEach { decoration ->
                        drawDecoration(decoration, rowIndex, width, height)
                    }
                }
            }
        }
    }
}

@Composable
private fun NvimTextSegment(
    segment: RenderedSegment,
    rowIndex: Int,
    cellWidth: Dp,
    cellHeight: Dp,
    blinkVisible: Boolean,
) {
    val targetWidth = cellWidth * segment.columns.toFloat()
    val segmentVisible = blinkVisible || !segment.blink

    Box(
        modifier = Modifier.offset(
            x = cellWidth * segment.startCol.toFloat(),
            y = cellHeight * rowIndex.toFloat(),
        ),
    ) {
        Canvas(
            modifier = Modifier
                .width(targetWidth)
                .height(cellHeight)
                .graphicsLayer()
                .alpha(if (segmentVisible) 1f else 0f),
        ) {
            NvimPerf.measure(segment.kind.drawMetric) {
                val targetPx = targetWidth.toPx()
                val layoutWidth = segment.layout.size.width.toFloat()
                if (
                    segment.wide &&
                    layoutWidth > 0f &&
                    abs(targetPx - layoutWidth) > 0.5f
                ) {
                    // Neovim reserves two cells for a wide glyph. Android's
                    // fallback font may not have exactly that advance, so fit
                    // the glyph to its cell box to keep the grid aligned.
                    scale(
                        scaleX = targetPx / layoutWidth,
                        scaleY = 1f,
                        pivot = Offset.Zero,
                    ) {
                        drawText(
                            textLayoutResult = segment.layout,
                            topLeft = Offset.Zero,
                        )
                    }
                } else {
                    drawText(
                        textLayoutResult = segment.layout,
                        topLeft = Offset.Zero,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawDecoration(
    decoration: RenderedDecoration,
    rowIndex: Int,
    cellWidth: Float,
    cellHeight: Float,
) {
    val x = decoration.startCol * cellWidth
    val width = decoration.columns * cellWidth
    val top = rowIndex * cellHeight
    val strokeWidth = (cellHeight * 0.06f).coerceAtLeast(1f)
    val startX = x + strokeWidth / 2f
    val endX = x + width - strokeWidth / 2f
    if (startX >= endX) return

    val bottom = top + cellHeight - strokeWidth
    val color = decoration.color

    when (decoration.kind) {
        DecorationKind.Underline -> drawLine(
            color = color,
            start = Offset(startX, bottom),
            end = Offset(endX, bottom),
            strokeWidth = strokeWidth,
        )

        DecorationKind.UnderDouble -> {
            drawLine(
                color = color,
                start = Offset(startX, bottom - strokeWidth * 2f),
                end = Offset(endX, bottom - strokeWidth * 2f),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = color,
                start = Offset(startX, bottom),
                end = Offset(endX, bottom),
                strokeWidth = strokeWidth,
            )
        }

        DecorationKind.UnderCurly -> {
            val amplitude = (cellHeight * 0.08f).coerceAtLeast(1f)
            val wavelength = (cellWidth * 2f).coerceAtLeast(2f)
            val path = Path()
            path.moveTo(startX, bottom)
            val step = (wavelength / 8f).coerceAtLeast(1f)
            var px = startX
            while (px <= endX) {
                val phase = ((px - startX) / wavelength) * 2f * PI.toFloat()
                path.lineTo(px, bottom + sin(phase) * amplitude)
                px += step
            }
            path.lineTo(endX, bottom)
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth),
            )
        }

        DecorationKind.UnderDotted -> drawLine(
            color = color,
            start = Offset(startX, bottom),
            end = Offset(endX, bottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(strokeWidth, strokeWidth * 2f),
            ),
        )

        DecorationKind.UnderDashed -> drawLine(
            color = color,
            start = Offset(startX, bottom),
            end = Offset(endX, bottom),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(strokeWidth * 3f, strokeWidth * 2f),
            ),
        )

        DecorationKind.Overline -> drawLine(
            color = color,
            start = Offset(startX, top + strokeWidth),
            end = Offset(endX, top + strokeWidth),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun rememberTextBlinkVisible(frame: NvimFrame): Boolean {
    val hasBlink = remember(frame.styles) {
        frame.styles.values.any { it.blink }
    }
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(hasBlink) {
        visible = true
        if (!hasBlink) return@LaunchedEffect

        while (true) {
            delay(TEXT_BLINK_INTERVAL_MS)
            visible = !visible
        }
    }

    return visible
}

private data class CursorVisual(
    val shape: CursorShape,
    val percentage: Int,
    val fill: Color,
    val textColor: Color,
    val textStyle: SpanStyle,
    val glyph: String?,
    val cellColumns: Int,
)

@Composable
private fun NvimCursorOverlay(
    frame: NvimFrame,
    rows: List<TextRow>,
    cursor: CursorState,
    cellWidth: Dp,
    cellHeight: Dp,
    textMeasurer: TextMeasurer,
    baseTextStyle: TextStyle,
    isScrolling: Boolean, // not show glyph when scrolling
) {
    val visual = remember(frame, rows, cursor) { frame.cursorVisual(rows, cursor) }
    val visible = rememberCursorVisible(frame.cursorMode, frame.isBusy)

    val glyphLayout = remember(visual, textMeasurer, baseTextStyle) {
        val glyph = visual.glyph ?: return@remember null
        textMeasurer.measure(
            text = AnnotatedString(glyph),
            style = baseTextStyle.merge(
                visual.textStyle.copy(
                    color = visual.textColor,
                    background = Color.Unspecified,
                ),
            ),
            softWrap = false,
            maxLines = 1,
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (!visible) return@Canvas

        val width = cellWidth.toPx()
        val height = cellHeight.toPx()
        val x = cursor.col * width
        val y = cursor.row * height
        val fraction = visual.percentage / 100f
        val cursorCellWidth = width * visual.cellColumns

        when (visual.shape) {
            CursorShape.BLOCK -> {
                drawRect(
                    color = visual.fill,
                    topLeft = Offset(x, y),
                    size = Size(cursorCellWidth, height),
                )
                if (!isScrolling) {
                    glyphLayout?.let { glyph ->
                        val glyphWidth = glyph.size.width.toFloat()
                        if (
                            visual.cellColumns > 1 &&
                            glyphWidth > 0f &&
                            abs(cursorCellWidth - glyphWidth) > 0.5f
                        ) {
                            translate(left = x, top = y) {
                                scale(
                                    scaleX = cursorCellWidth / glyphWidth,
                                    scaleY = 1f,
                                    pivot = Offset.Zero,
                                ) {
                                    drawText(textLayoutResult = glyph, topLeft = Offset.Zero)
                                }
                            }
                        } else {
                            drawText(textLayoutResult = glyph, topLeft = Offset(x, y))
                        }
                    }
                }
            }

            CursorShape.HORIZONTAL -> {
                val cursorHeight = height * fraction
                drawRect(
                    color = visual.fill,
                    topLeft = Offset(x, y + height - cursorHeight),
                    size = Size(cursorCellWidth, cursorHeight),
                )
            }

            CursorShape.VERTICAL -> {
                val cursorWidth = width * fraction
                drawRect(
                    color = visual.fill,
                    topLeft = Offset(x, y),
                    size = Size(cursorWidth, height),
                )
            }
        }
    }
}

@Composable
private fun rememberCursorVisible(mode: ModeProperty?, busy: Boolean): Boolean {
    val wait = mode?.blink_wait?.milliseconds ?: 0L.milliseconds
    val on = mode?.blink_on?.milliseconds ?: 0L.milliseconds
    val off = mode?.blink_off?.milliseconds ?: 0L.milliseconds
    var visible by remember(wait, on, off) { mutableStateOf(!busy) }

    LaunchedEffect(wait, on, off, busy) {
        if (busy) {
            visible = false
            return@LaunchedEffect
        }

        visible = true
        if (on <= 0L.milliseconds || off <= 0L.milliseconds) return@LaunchedEffect

        if (wait > 0L.milliseconds) delay(wait)
        while (true) {
            visible = false
            delay(off)
            visible = true
            delay(on)
        }
    }

    return visible
}

/**
 * Resolves the colors and glyph used to draw the cursor at the current cell.
 */
private fun NvimFrame.cursorVisual(rows: List<TextRow>, cursor: CursorState): CursorVisual {
    val mode = cursorMode
    val shape = mode?.cursor_shape ?: CursorShape.BLOCK
    val defaultPercentage = if (shape == CursorShape.BLOCK) 100L else 25L
    val percentage = (mode?.cell_percentage?.takeIf { it > 0L } ?: defaultPercentage)
        .toInt()
        .coerceIn(1, 100)

    val row = rows.getOrNull(cursor.row)
    val span = row?.spans?.firstOrNull { cursor.col in it.startCol until it.endCol }
    val cellHighlight = span?.let { highlightFor(it.hlId) } ?: NvimHighlight.Default
    val cellStyle = cellHighlight.spanStyle(defaultColors)
    val modeHighlight = mode?.attr_id?.takeIf { it != 0L }?.let { highlightFor(it.toInt()) }
    val attrStyle = modeHighlight?.spanStyle(defaultColors)

    val cellForeground = cellStyle.color.orElse(defaultColors.color)
    val cellBackground = cellStyle.background.orElse(defaultColors.background)
    val attrForeground = attrStyle?.color?.takeUnless { it == Color.Unspecified }
    val attrBackground = attrStyle?.background?.takeUnless { it == Color.Unspecified }

    val fill = attrBackground ?: attrForeground ?: cellForeground
    val textColor = when {
        shape != CursorShape.BLOCK -> cellBackground
        attrBackground != null -> attrForeground ?: cellBackground
        else -> cellBackground
    }

    val cellColumns = if (span?.wide == true) span.columns else 1
    val glyph = if (shape == CursorShape.BLOCK && span != null && !cellHighlight.conceal) {
        val offset = cursor.col - span.startCol
        span.cellTextAt(offset)?.takeUnless { it == " " }
    } else {
        null
    }

    return CursorVisual(
        shape = shape,
        percentage = percentage,
        fill = fill,
        textColor = textColor,
        textStyle = cellStyle,
        glyph = glyph,
        cellColumns = cellColumns,
    )
}

private fun Color.orElse(fallback: Color): Color = if (this == Color.Unspecified) fallback else this

/**
 * Transparent gesture layer over the grid. It handles both touch and
 * hardware-mouse events, translating them into `nvim_input_mouse` events.
 */
@Composable
private fun NvimGestureOverlay(
    cellWidthPx: Float,
    cellHeightPx: Float,
    columns: Int,
    rows: Int,
    hasSelection: Boolean,
    selectionMode: Boolean,
    hitTest: (row: Int, col: Int) -> NvimGridHit?,
    onOpenUrl: (String) -> Unit,
    onMouse: (NvimMouseEvent) -> Unit,
    takeModifiers: () -> String,
    onContextMenu: (row: Int, col: Int) -> Unit,
    onSelectionChange: (Int, Int, Int, Int) -> Unit,
    onSelectionEnd: (Int, Int, Int, Int) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentHitTest by rememberUpdatedState(hitTest)
    val currentOnOpenUrl by rememberUpdatedState(onOpenUrl)
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentTakeModifiers by rememberUpdatedState(takeModifiers)
    val currentOnContextMenu by rememberUpdatedState(onContextMenu)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentOnSelectionEnd by rememberUpdatedState(onSelectionEnd)
    val currentOnClearSelection by rememberUpdatedState(onClearSelection)

    AndroidView(
        factory = { context -> NvimGestureView(context) },
        update = { view ->
            view.columns = columns
            view.rows = rows
            view.cellWidthPx = cellWidthPx.coerceAtLeast(1f)
            view.cellHeightPx = cellHeightPx.coerceAtLeast(1f)
            view.hasSelection = hasSelection
            view.selectionMode = selectionMode
            view.hitTest = currentHitTest
            view.onOpenUrl = currentOnOpenUrl
            view.onMouse = currentOnMouse
            view.takeModifiers = currentTakeModifiers
            view.onContextMenu = currentOnContextMenu
            view.onSelectionChange = currentOnSelectionChange
            view.onSelectionEnd = currentOnSelectionEnd
            view.onClearSelection = currentOnClearSelection
        },
        modifier = modifier,
    )
}

/**
 * The replacement to the old method (hidden text input)
 * This mirrors Termux-app TerminalView's approach and keyboard configs
 */
@Composable
internal fun NvimInputCapture(view: NvimInputView, onInput: (String) -> Unit) {
    AndroidView(
        factory = { view },
        update = { it.onInput = onInput },
        modifier = Modifier
            .size(1.dp)
            .alpha(0f),
    )
}

internal class NvimInputView(context: Context) : View(context) {
    var onInput: (String) -> Unit = {}

    // Some IMEs (Unexpected Keyboard) send modifier as a seperate key event
    // then commit the character through commitText, which carries no meta state
    // Need track the modifiers to apply
    private var ctrlDown = false
    private var altDown = false
    private var shiftDown = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun focusAndShowKeyboard() {
        requestFocus()
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, 0)
        }
    }

    fun giveUpFocus() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // options based on termux
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this@NvimInputView, true) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (this@NvimInputView.handleKeyEvent(event)) return true
                return super.sendKeyEvent(event)
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                super.commitText(text, newCursorPosition)
                sendEditable()
                return true
            }

            override fun finishComposingText(): Boolean {
                super.finishComposingText()
                sendEditable()
                return true
            }

            override fun deleteSurroundingText(leftLength: Int, rightLength: Int): Boolean {
                if (leftLength > 0) {
                    this@NvimInputView.onInput("<BS>".repeat(leftLength))
                }
                return super.deleteSurroundingText(leftLength, rightLength)
            }

            private fun sendEditable() {
                val content = editable ?: return
                if (content.isNotEmpty()) {
                    this@NvimInputView.onInput(
                        content.toNvimKeys(ctrl = ctrlDown, alt = altDown),
                    )
                    content.clear()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKeyEvent(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKeyEvent(event)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        if (updateModifierState(event.keyCode, down)) return true
        if (!down) return false

        val keys = event.toNvimKeys(
            extraCtrl = ctrlDown,
            extraAlt = altDown,
            extraShift = shiftDown,
        ) ?: return false
        onInput(keys)
        return true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) resetModifiers()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) resetModifiers()
    }

    private fun updateModifierState(keyCode: Int, down: Boolean): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> ctrlDown = down
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> altDown = down
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> shiftDown = down
            else -> return false
        }
        return true
    }

    private fun resetModifiers() {
        ctrlDown = false
        altDown = false
        shiftDown = false
    }
}

private const val GLOBAL_GRID = 1L
private val RESIZE_DEBOUNCE_MS: Duration = 100.milliseconds
private val TEXT_BLINK_INTERVAL_MS: Duration = 500.milliseconds

private const val SCROLL_OVERDRAW_ROWS = 1

// nvim key notations
private fun CharSequence.toNvimKeys(ctrl: Boolean = false, alt: Boolean = false): String =
    buildString(length) {
        for (char in this@toNvimKeys) {
            when {
                char == '\n' || char == '\r' -> append("<CR>")

                char == '\t' -> append('\t')

                char == '\u001b' -> append("<Esc>")

                // IMEs that commit Ctrl combinations as raw control bytes
                char.code in 0..0x1f || char.code == 0x7f -> {
                    val control = char.toNvimControlKey()
                    if (control != null) append(control) else append(char)
                }

                ctrl || alt -> append(modifiedKeyNotation(char, ctrl, alt))

                char == '<' -> append("<LT>")

                else -> append(char)
            }
        }
    }

private fun Int.toCharacter(): Char? = when (this) {
    in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 'a' + (this - KeyEvent.KEYCODE_A)

    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0' + (this - KeyEvent.KEYCODE_0)

    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
        '0' + (this - KeyEvent.KEYCODE_NUMPAD_0)

    KeyEvent.KEYCODE_GRAVE -> '`'

    KeyEvent.KEYCODE_MINUS -> '-'

    KeyEvent.KEYCODE_EQUALS -> '='

    KeyEvent.KEYCODE_LEFT_BRACKET -> '['

    KeyEvent.KEYCODE_RIGHT_BRACKET -> ']'

    KeyEvent.KEYCODE_BACKSLASH -> '\\'

    KeyEvent.KEYCODE_SEMICOLON -> ';'

    KeyEvent.KEYCODE_APOSTROPHE -> '\''

    KeyEvent.KEYCODE_SLASH -> '/'

    KeyEvent.KEYCODE_AT -> '@'

    KeyEvent.KEYCODE_PLUS -> '+'

    KeyEvent.KEYCODE_COMMA -> ','

    KeyEvent.KEYCODE_PERIOD -> '.'

    KeyEvent.KEYCODE_STAR -> '*'

    KeyEvent.KEYCODE_POUND -> '#'

    KeyEvent.KEYCODE_SPACE -> ' '

    else -> null
}

private fun Char.toNvimControlKey(): String? = when {
    this == '\u007f' -> "<BS>"

    code !in 0..0x1f -> null

    else -> {
        val name = (code + 0x40).toChar()
        val key = if (name in 'A'..'Z') name.lowercaseChar().toString() else name.toString()
        "<C-$key>"
    }
}

private fun modifiedKeyNotation(char: Char, ctrl: Boolean, alt: Boolean): String {
    val prefix = buildString {
        if (ctrl) append("C-")
        if (alt) append("M-")
    }
    val key = when (char) {
        ' ' -> "Space"
        '<' -> "lt"
        else -> char.toString()
    }
    return "<$prefix$key>"
}

private fun KeyEvent.toNvimKeys(
    extraCtrl: Boolean = false,
    extraAlt: Boolean = false,
    extraShift: Boolean = false,
): String? {
    val ctrl = isCtrlPressed || extraCtrl
    val alt = isAltPressed || extraAlt
    val shift = isShiftPressed || extraShift

    if (
        keyCode == KeyEvent.KEYCODE_TAB &&
        !ctrl &&
        !alt &&
        !shift
    ) {
        return "\t"
    }

    val named = when (keyCode) {
        KeyEvent.KEYCODE_DEL -> "BS"
        KeyEvent.KEYCODE_FORWARD_DEL -> "Del"
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "CR"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_ESCAPE -> "Esc"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_MOVE_HOME -> "Home"
        KeyEvent.KEYCODE_MOVE_END -> "End"
        KeyEvent.KEYCODE_PAGE_UP -> "PageUp"
        KeyEvent.KEYCODE_PAGE_DOWN -> "PageDown"
        KeyEvent.KEYCODE_INSERT -> "Insert"
        else -> null
    }

    if (named != null) {
        val modifiers = buildString {
            if (ctrl) append("C-")
            if (alt) append("M-")
            if (shift) append("S-")
        }
        return "<$modifiers$named>"
    }

    val unicode = unicodeChar
    val base = if (ctrl || alt) getUnicodeChar(0) else unicode
    val char = when {
        base != 0 -> base.toChar()
        else -> keyCode.toCharacter() ?: return null
    }
    if (Character.isISOControl(char)) {
        // A raw control byte, like Ctrl-C as 0x03.
        return char.toNvimControlKey()
    }

    val modifiers = buildString {
        if (ctrl) append("C-")
        if (alt) append("M-")
    }
    val key = when (char) {
        '<' -> "lt"
        ' ' -> "Space"
        else -> char.toString()
    }
    return if (modifiers.isEmpty()) key.toNvimKeys() else "<$modifiers$key>"
}
