package com.ngtv.neodroid.nvim.render

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

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
            .pointerInput(Unit) {
                detectTapGestures {
                    focusRequester.requestFocus()
                    keyboardController?.show()
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
            focusRequester = focusRequester,
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

// A hidden text field captures soft keyboard input.
// TODO: is there a better way?
@Composable
private fun NvimInputCapture(focusRequester: FocusRequester, onInput: (String) -> Unit) {
    val currentOnInput by rememberUpdatedState(onInput)

    var inputValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = INPUT_SENTINEL,
                selection = TextRange(INPUT_SENTINEL.length),
            ),
        )
    }

    BasicTextField(
        value = inputValue,
        onValueChange = { newValue ->
            currentOnInput(encodeNvimInput(inputValue.text, newValue.text))

            val sanitized = if (INPUT_SENTINEL in newValue.text) {
                newValue
            } else {
                TextFieldValue(
                    text = newValue.text + INPUT_SENTINEL,
                    selection = TextRange(
                        newValue.text.length + INPUT_SENTINEL.length,
                    ),
                )
            }

            inputValue = if (
                sanitized.composition == null &&
                sanitized.text.length > MAX_INPUT_SCRATCH
            ) {
                TextFieldValue(
                    text = INPUT_SENTINEL,
                    selection = TextRange(INPUT_SENTINEL.length),
                )
            } else {
                sanitized
            }
        },
        modifier = Modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focusRequester),
        textStyle = TextStyle(
            color = Color.Transparent,
            fontSize = 1.sp,
        ),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.None,
        ),
        singleLine = false,
    )
}

private const val INPUT_SENTINEL = "\u200B"
private const val MAX_INPUT_SCRATCH = 4096
private val RESIZE_DEBOUNCE_MS: Duration = 100.milliseconds

// nvim key notations
private fun encodeNvimInput(oldText: String, newText: String): String {
    val prefix = oldText.commonPrefixWith(newText).length
    val maxSuffix = minOf(oldText.length, newText.length) - prefix
    val suffix = oldText.commonSuffixWith(newText).length.coerceAtMost(maxSuffix)
    val removed = oldText.length - prefix - suffix
    val inserted = newText.substring(prefix, newText.length - suffix)

    return buildString {
        repeat(removed) { append("<BS>") }
        inserted.forEach { char ->
            when (char) {
                '\n', '\r' -> append("<CR>")
                '\t' -> append("<Tab>")
                '<' -> append("<LT>")
                else -> append(char)
            }
        }
    }
}
