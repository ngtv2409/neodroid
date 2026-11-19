package com.ngtv.neodroid.nvim.render

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngtv.neodroid.R
import com.ngtv.neodroid.core.events.CursorShape
import com.ngtv.neodroid.core.events.ModeProperty
import com.ngtv.neodroid.nvim.NvimFrame
import com.ngtv.neodroid.nvim.input.NvimGestureView
import com.ngtv.neodroid.nvim.input.NvimMouseEvent
import com.ngtv.neodroid.perf.NvimPerf
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NvimEditor(
    frame: NvimFrame,
    onResize: suspend (width: Int, height: Int) -> Unit,
    onMouse: (NvimMouseEvent) -> Unit,
    takeModifiers: () -> String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val baseTextStyle = remember {
        TextStyle(
            // TODO: proper font management
            fontFamily = FontFamily(Font(R.font.jetbrains_mono_nerdfont_bold)),
            fontSize = 14.sp,
            lineHeight = 18.sp,
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

        if (frame.rows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Waiting for Neovim UI...")
            }
        } else {
            val renderedRows = remember(
                frame,
                visibleRows,
                renderCache,
                textMeasurer,
                baseTextStyle,
            ) {
                NvimPerf.measure("render.compose") {
                    renderCache.render(
                        frame = frame,
                        maxRows = visibleRows,
                        textMeasurer = textMeasurer,
                        baseStyle = baseTextStyle,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(cellWidth * columns.toFloat())
                    .height(cellHeight * visibleRows.toFloat()),
            ) {
                NvimGrid(
                    rows = renderedRows,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                )
                NvimCursorOverlay(
                    frame = frame,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    textMeasurer = textMeasurer,
                    baseTextStyle = baseTextStyle,
                )
                NvimGestureOverlay(
                    cellWidthPx = with(density) { cellWidth.toPx() },
                    cellHeightPx = with(density) { cellHeight.toPx() },
                    columns = columns,
                    rows = visibleRows,
                    onMouse = onMouse,
                    takeModifiers = takeModifiers,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun NvimGrid(rows: List<RenderedRow>, cellWidth: Dp, cellHeight: Dp) {
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
                )
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
) {
    val density = LocalDensity.current
    val width = with(density) { segment.layout.size.width.toDp() }

    Box(
        modifier = Modifier.offset(
            x = cellWidth * segment.startCol.toFloat(),
            y = cellHeight * rowIndex.toFloat(),
        ),
    ) {
        Canvas(
            modifier = Modifier
                .width(width)
                .height(cellHeight)
                .graphicsLayer(),
        ) {
            NvimPerf.measure(segment.kind.drawMetric) {
                drawText(
                    textLayoutResult = segment.layout,
                    topLeft = Offset.Zero,
                )
            }
        }
    }
}

private data class CursorVisual(
    val shape: CursorShape,
    val percentage: Int,
    val fill: Color,
    val textColor: Color,
    val textStyle: SpanStyle,
    val glyph: String?,
)

@Composable
private fun NvimCursorOverlay(
    frame: NvimFrame,
    cellWidth: Dp,
    cellHeight: Dp,
    textMeasurer: TextMeasurer,
    baseTextStyle: TextStyle,
) {
    val visual = remember(frame) { frame.cursorVisual() }
    val visible = rememberCursorVisible(frame.cursorMode)

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
        val x = frame.cursor.col * width
        val y = frame.cursor.row * height
        val fraction = visual.percentage / 100f

        when (visual.shape) {
            CursorShape.BLOCK -> {
                drawRect(
                    color = visual.fill,
                    topLeft = Offset(x, y),
                    size = Size(width, height),
                )
                glyphLayout?.let { drawText(textLayoutResult = it, topLeft = Offset(x, y)) }
            }

            CursorShape.HORIZONTAL -> {
                val cursorHeight = height * fraction
                drawRect(
                    color = visual.fill,
                    topLeft = Offset(x, y + height - cursorHeight),
                    size = Size(width, cursorHeight),
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
private fun rememberCursorVisible(mode: ModeProperty?): Boolean {
    val wait = mode?.blink_wait?.milliseconds ?: 0L.milliseconds
    val on = mode?.blink_on?.milliseconds ?: 0L.milliseconds
    val off = mode?.blink_off?.milliseconds ?: 0L.milliseconds
    var visible by remember(wait, on, off) { mutableStateOf(true) }

    LaunchedEffect(wait, on, off) {
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
private fun NvimFrame.cursorVisual(): CursorVisual {
    val mode = cursorMode
    val shape = mode?.cursor_shape ?: CursorShape.BLOCK
    val defaultPercentage = if (shape == CursorShape.BLOCK) 100L else 25L
    val percentage = (mode?.cell_percentage?.takeIf { it > 0L } ?: defaultPercentage)
        .toInt()
        .coerceIn(1, 100)

    val cursor = cursor
    val row = rows.getOrNull(cursor.row)
    val span = row?.spans?.firstOrNull { cursor.col in it.startCol until it.endCol }
    val cellStyle = when {
        span == null || span.hlId == -1 -> defaultColors
        else -> styles[span.hlId] ?: defaultColors
    }

    val cellForeground = cellStyle.color.orElse(defaultColors.color)
    val cellBackground = cellStyle.background.orElse(defaultColors.background)
    val attrStyle = mode?.attr_id?.takeIf { it != 0L }?.let { styles[it.toInt()] }
    val attrForeground = attrStyle?.color?.takeUnless { it == Color.Unspecified }
    val attrBackground = attrStyle?.background?.takeUnless { it == Color.Unspecified }

    val fill = attrBackground ?: attrForeground ?: cellForeground
    val textColor = when {
        shape != CursorShape.BLOCK -> cellBackground
        attrBackground != null -> attrForeground ?: cellBackground
        else -> cellBackground
    }

    val glyph = if (shape == CursorShape.BLOCK && span != null) {
        val offset = cursor.col - span.startCol
        span.text.getOrNull(offset)?.takeUnless { it == ' ' }?.toString()
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
    onMouse: (NvimMouseEvent) -> Unit,
    takeModifiers: () -> String,
    modifier: Modifier = Modifier,
) {
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentTakeModifiers by rememberUpdatedState(takeModifiers)

    AndroidView(
        factory = { context -> NvimGestureView(context) },
        update = { view ->
            view.columns = columns
            view.rows = rows
            view.cellWidthPx = cellWidthPx.coerceAtLeast(1f)
            view.cellHeightPx = cellHeightPx.coerceAtLeast(1f)
            view.onMouse = currentOnMouse
            view.takeModifiers = currentTakeModifiers
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
                    this@NvimInputView.onInput(content.toNvimKeys())
                    content.clear()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val keys = event.toNvimKeys() ?: return super.onKeyDown(keyCode, event)
        onInput(keys)
        return true
    }
}

private val RESIZE_DEBOUNCE_MS: Duration = 100.milliseconds

// nvim key notations
private fun CharSequence.toNvimKeys(): String = buildString(length) {
    for (char in this@toNvimKeys) {
        when (char) {
            '\n', '\r' -> append("<CR>")

            // A raw tab is handled consistently by both Neovim and Android IMEs.
            '\t' -> append('\t')

            '<' -> append("<LT>")

            else -> append(char)
        }
    }
}

private fun KeyEvent.toNvimKeys(): String? {
    if (
        keyCode == KeyEvent.KEYCODE_TAB &&
        !isCtrlPressed &&
        !isAltPressed &&
        !isShiftPressed
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
            if (isCtrlPressed) append("C-")
            if (isAltPressed) append("M-")
            if (isShiftPressed) append("S-")
        }
        return "<$modifiers$named>"
    }

    val unicode = unicodeChar
    if (unicode == 0) return null

    val base = if (isCtrlPressed || isAltPressed) getUnicodeChar(0) else unicode
    if (base == 0) return null

    val char = base.toChar()
    if (Character.isISOControl(char)) return null

    val modifiers = buildString {
        if (isCtrlPressed) append("C-")
        if (isAltPressed) append("M-")
    }
    val key = when (char) {
        '<' -> "lt"
        ' ' -> "Space"
        else -> char.toString()
    }
    return if (modifiers.isEmpty()) key.toNvimKeys() else "<$modifiers$key>"
}
