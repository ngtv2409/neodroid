package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.R
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.nvim.NvimFrame
import com.ngtv.neodroid.nvim.NvimSession
import com.ngtv.neodroid.nvim.TextSpan
import com.ngtv.neodroid.sharedComponents.PasswordInput
import com.ngtv.neodroid.sharedComponents.ValidatedTextField
import com.ngtv.neodroid.sshUtils.NvimTool
import com.ngtv.neodroid.sshUtils.SshSessionManager
import com.ngtv.neodroid.sshUtils.WorkspaceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException
import kotlin.collections.setValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SshSessionScreen(session: WorkspaceSession) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(-1) }
    val pagerState = rememberPagerState { session.tools.size }

    var currentId by remember { mutableStateOf("") }
    var selectedToolAdd by remember { mutableStateOf<ToolType?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Display each error and automatically dismiss it after 3 seconds.
    LaunchedEffect(errorMessage) {
        val error = errorMessage ?: return@LaunchedEffect

        snackbarHostState.showSnackbar(
            message = error,
            duration = SnackbarDuration.Short,
        )

        errorMessage = null
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (session.tools.isNotEmpty()) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab.coerceIn(
                        0,
                        (session.tools.size - 1).coerceAtLeast(0),
                    ),
                    edgePadding = 4.dp,
                    divider = {},
                ) {
                    session.tools.forEachIndexed { index, tool ->
                        Tab(
                            selected = selectedTab == index,
                            modifier = Modifier.height(40.dp),
                            onClick = {
                                selectedTab = index
                                currentId = tool.id
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = tool.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )

                                    IconButton(
                                        modifier = Modifier.height(28.dp),
                                        onClick = {
                                            scope.launch {
                                                val closedIndex =
                                                    session.tools.indexOfFirst {
                                                        it.id == tool.id
                                                    }
                                                val currentIndex =
                                                    session.tools.indexOfFirst {
                                                        it.id == currentId
                                                    }

                                                withContext(Dispatchers.IO) {
                                                    session.closeTool(tool.id)
                                                }

                                                selectedTab =
                                                    if (tool.id == currentId) {
                                                        when {
                                                            session.tools.isEmpty() -> -1

                                                            closedIndex == session.tools.size ->
                                                                closedIndex - 1

                                                            closedIndex > 0 -> closedIndex

                                                            else -> 0
                                                        }
                                                    } else if (
                                                        closedIndex < currentIndex
                                                    ) {
                                                        currentIndex - 1
                                                    } else {
                                                        currentIndex
                                                    }

                                                if (session.tools.isNotEmpty()) {
                                                    val newIndex = selectedTab.coerceIn(
                                                        0,
                                                        session.tools.lastIndex,
                                                    )
                                                    selectedTab = newIndex
                                                    currentId =
                                                        session.tools[newIndex].id
                                                    pagerState.scrollToPage(newIndex)
                                                } else {
                                                    currentId = ""
                                                }
                                            }
                                        },
                                    ) {
                                        Text("×")
                                    }
                                }
                            },
                        )
                    }

                    AddToolDropdown(
                        onToolSelected = { tool ->
                            selectedToolAdd = tool
                        },
                    )
                }
            } else {
                Row(
                    modifier = Modifier.height(40.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AddToolDropdown(
                        onToolSelected = { tool ->
                            selectedToolAdd = tool
                        },
                    )
                }
            }

            if (session.tools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No tools available")
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = false,
                ) { page ->
                    when (val tool = session.tools[page]) {
                        is NvimTool -> {
                            if (tool.connectionFailure != null) {
                                Text("Error: ${tool.connectionFailure}")
                            } else {
                                val frame by tool.nvimSession.state
                                    .collectAsStateWithLifecycle()
                                NvimFrameRender(
                                    frame = frame,
                                    onInput = { input ->
                                        tool.nvimSession.sendInput(input)
                                    },
                                    onResize = { width, height ->
                                        tool.nvimSession.resize(width, height)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedToolAdd?.let { tool ->
        CreateToolDialog(
            tool = tool,
            onDismiss = { selectedToolAdd = null },
            onSubmit = { inputMap ->
                selectedToolAdd = null

                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            when (tool) {
                                ToolType.NvimEmbedded -> session.startNvim()

                                ToolType.NvimUnixSocket ->
                                    session.startNvimConnectToSock(
                                        inputMap["socketpath"]!!,
                                    )
                            }
                        }

                        val lastIndex = session.tools.lastIndex
                        if (lastIndex >= 0) {
                            selectedTab = lastIndex
                            currentId = session.tools[lastIndex].id
                            pagerState.scrollToPage(lastIndex)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message ?: e}"
                    }
                }
            },
        )
    }
}

@Composable
fun NvimFrameRender(
    frame: NvimFrame,
    onInput: (String) -> Unit,
    onResize: suspend (width: Int, height: Int) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val fontSize = 14.sp
    val lineHeight = 18.sp

    val baseTextStyle = TextStyle(
        // TODO: proper font management
        fontFamily = FontFamily(Font(R.font.jetbrains_mono_nerdfont_bold)),
        fontSize = fontSize,
        lineHeight = lineHeight,
    )

    val cellMetrics = remember(textMeasurer, baseTextStyle, density) {
        textMeasurer.measure(
            text = AnnotatedString("M"),
            style = baseTextStyle,
        ).size
    }

    val cellWidthPx = cellMetrics.width.toFloat()
    val cellHeightPx = cellMetrics.height.toFloat()

    val cellWidthDp = with(density) {
        cellMetrics.width.toDp()
    }

    val cellHeightDp = with(density) {
        cellMetrics.height.toDp()
    }

    val defaultStyle = frame.defaultColors
    val renderCache = remember(textMeasurer, baseTextStyle, density) {
        NvimFrameRenderCache()
    }
    val renderedRows = remember(frame, renderCache, textMeasurer, baseTextStyle) {
        renderCache.render(frame, textMeasurer, baseTextStyle)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(defaultStyle.background)
            .pointerInput(Unit) {
                detectTapGestures {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            },
    ) {
        val columns = (maxWidth / cellWidthDp).toInt().coerceAtLeast(1)
        val visibleRows = (maxHeight / cellHeightDp).toInt().coerceAtLeast(1)

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
            Canvas(
                modifier = Modifier
                    .width(cellWidthDp * columns)
                    .height(cellHeightDp * visibleRows),
            ) {
                renderedRows.forEachIndexed { rowIndex, row ->
                    row.forEach { span ->
                        if (span.background != Color.Unspecified) {
                            drawRect(
                                color = span.background,
                                topLeft = Offset(
                                    x = span.startCol * cellWidthPx,
                                    y = rowIndex * cellHeightPx,
                                ),
                                size = androidx.compose.ui.geometry.Size(
                                    width = span.columns * cellWidthPx,
                                    height = cellHeightPx,
                                ),
                            )
                        }

                        drawText(
                            textLayoutResult = span.layout,
                            topLeft = Offset(
                                x = span.startCol * cellWidthPx,
                                y = rowIndex * cellHeightPx,
                            ),
                        )
                    }
                }

                // TODO: handle cursor style properly
                // also we'll need to invert text under cursor (complicated)
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(
                        x = frame.cursor.col * cellWidthPx,
                        y = frame.cursor.row * cellHeightPx,
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        width = cellWidthPx / 3,
                        height = cellHeightPx,
                    ),
                )
            }
        }

        NvimInputCapture(
            focusRequester = focusRequester,
            onInput = onInput,
        )
    }
}

private class RenderedSpan(
    val startCol: Int,
    val columns: Int,
    val background: Color,
    val layout: TextLayoutResult,
)

private class NvimFrameRenderCache {
    private class CachedLayout(val style: SpanStyle, val layout: TextLayoutResult)

    private val layouts = HashMap<TextSpan, CachedLayout>()

    fun render(
        frame: NvimFrame,
        textMeasurer: TextMeasurer,
        baseStyle: TextStyle,
    ): List<List<RenderedSpan>> {
        val liveSpans = HashSet<TextSpan>()
        val rows = ArrayList<List<RenderedSpan>>(frame.rows.size)

        frame.rows.forEach { row ->
            val renderedSpans = ArrayList<RenderedSpan>(row.spans.size)

            row.spans.forEach { span ->
                liveSpans.add(span)

                val style = if (span.hlId == -1) {
                    frame.defaultColors
                } else {
                    frame.styles[span.hlId] ?: frame.defaultColors
                }

                val cached = layouts[span]
                val layout = if (cached == null || cached.style != style) {
                    textMeasurer.measure(
                        text = AnnotatedString(span.text),
                        style = baseStyle.merge(style.copy(background = Color.Unspecified)),
                        softWrap = false,
                        maxLines = 1,
                    ).also { measured ->
                        layouts[span] = CachedLayout(style, measured)
                    }
                } else {
                    cached.layout
                }

                renderedSpans.add(
                    RenderedSpan(
                        startCol = span.startCol,
                        columns = span.columns,
                        background = style.background,
                        layout = layout,
                    ),
                )
            }

            rows.add(renderedSpans)
        }

        if (layouts.size > liveSpans.size * 2 + 32) {
            layouts.keys.retainAll(liveSpans)
        }

        return rows
    }
}

// a hidden text field captures soft keyboard input.
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

@Composable
private fun AddToolDropdown(onToolSelected: (ToolType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
        ) {
            Text("+")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ToolType.entries.forEach { tool ->
                DropdownMenuItem(
                    text = { Text(tool.label) },
                    onClick = {
                        expanded = false
                        onToolSelected(tool)
                    },
                )
            }
        }
    }
}

@Composable
private fun CreateToolDialog(
    tool: ToolType,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, String>) -> Unit,
) {
    val inputs: SnapshotStateMap<String, String> = remember(tool) {
        mutableStateMapOf<String, String>().apply {
            putAll(tool.createDefaultMutableMap())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(tool.label)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(tool.desc)
                tool.fields.forEach { f ->
                    ValidatedTextField(
                        value = inputs[f.key]!!.ifBlank { "" },
                        label = f.label,
                        onValueChange = { inputs[f.key] = it },
                        validator = f.validator,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = tool.isValidMap(inputs),
                onClick = {
                    onSubmit(inputs)
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private data class ToolField(
    val key: String,
    val label: String,
    val default: String = "",
    val validator: (String) -> String? = { null },
)
private enum class ToolType(
    val label: String,
    val desc: String = "",
    val fields: List<ToolField> = emptyList(),
) {
    NvimEmbedded("Launch Neovim"),
    NvimUnixSocket(
        "Connect to Nvim over UNIX socket",
        "Needs socat installed on remote",
        listOf(
            ToolField("socketpath", "Socket path", "") {
                if (it.isBlank()) {
                    "Socket path is required"
                } else {
                    null
                }
            },
        ),
    ),
    ;

    fun createDefaultMutableMap(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        this.fields.forEach {
            map[it.key] = it.default
        }
        return map
    }

    fun isValidMap(map: Map<String, String>): Boolean {
        // every declared fields exist and validator passed
        return this.fields.all {
            map.containsKey(it.key) && it.validator(map[it.key]!!) == null
        }
    }
}
