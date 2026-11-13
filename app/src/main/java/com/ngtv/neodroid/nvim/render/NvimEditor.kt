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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
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
import com.ngtv.neodroid.nvim.CursorState
import com.ngtv.neodroid.nvim.NvimFrame
import com.ngtv.neodroid.perf.NvimPerf
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NvimEditor(
    frame: NvimFrame,
    onInput: (String) -> Unit,
    onResize: suspend (width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val context = LocalContext.current
    val inputView = remember(context) { NvimInputView(context) }

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
            .background(frame.defaultColors.background)
            .pointerInput(inputView) {
                detectTapGestures {
                    inputView.focusAndShowKeyboard()
                }
            },
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
                    cursor = frame.cursor,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                )
            }
        }

        NvimInputCapture(
            view = inputView,
            onInput = onInput,
        )
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

@Composable
private fun NvimCursorOverlay(cursor: CursorState, cellWidth: Dp, cellHeight: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = cellWidth.toPx()
        val height = cellHeight.toPx()

        // TODO: handle cursor style properly and invert text under the cursor.
        drawRect(
            color = Color.Black,
            topLeft = Offset(
                x = cursor.col * width,
                y = cursor.row * height,
            ),
            size = Size(
                width = width / 3,
                height = height,
            ),
        )
    }
}

/**
 * The replacement to the old method (hidden text input)
 * This mirrors Termux-app TerminalView's approach and keyboard configs
 */
@Composable
private fun NvimInputCapture(view: NvimInputView, onInput: (String) -> Unit) {
    AndroidView(
        factory = { view },
        update = { it.onInput = onInput },
        modifier = Modifier
            .size(1.dp)
            .alpha(0f),
    )
}

private class NvimInputView(context: Context) : View(context) {
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
