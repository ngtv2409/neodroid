package com.ngtv.neodroid.ui.screens.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.nvim.input.ExtraKeyAction
import com.ngtv.neodroid.nvim.input.rememberExtraKeysState
import com.ngtv.neodroid.nvim.render.NvimEditor
import com.ngtv.neodroid.nvim.render.NvimInputCapture
import com.ngtv.neodroid.nvim.render.NvimInputView
import com.ngtv.neodroid.settings.EditorFont
import com.ngtv.neodroid.settings.Settings
import com.ngtv.neodroid.settings.rememberSettingsRepository
import com.ngtv.neodroid.sshUtils.NvimTool
import com.ngtv.neodroid.sshUtils.WorkspaceSession
import com.ngtv.neodroid.sshUtils.WorkspaceTool
import com.ngtv.neodroid.ui.components.EmptyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SshSessionScreen(session: WorkspaceSession, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val initialIndex = remember(session.id) {
        session.tools.indexOfFirst { it.id == session.activeToolId }
            .takeIf { it >= 0 }
            ?: 0
    }

    var selectedIndex by remember(session.id) { mutableIntStateOf(initialIndex) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { session.tools.size }
    val currentTool = session.tools.getOrNull(selectedIndex)
    val extraKeysState = rememberExtraKeysState()

    val settings = rememberSettingsRepository()
    val editorFontKey by settings.getAsFlow(Settings.editorFontFamily)
        .collectAsStateWithLifecycle(
            initialValue = Settings.editorFontFamily.defaultValue,
        )
    val editorFontSize by settings.getAsFlow(Settings.editorFontSize)
        .collectAsStateWithLifecycle(
            initialValue = Settings.editorFontSize.defaultValue,
        )
    val editorLineHeight by settings.getAsFlow(Settings.editorLineHeight)
        .collectAsStateWithLifecycle(
            initialValue = Settings.editorLineHeight.defaultValue,
        )
    val editorFontFamily = remember(editorFontKey) {
        EditorFont.fromKey(editorFontKey).fontFamily()
    }
    val editorFontSizeSp = editorFontSize.sp
    val editorLineHeightSp = (editorFontSize * editorLineHeight / 100f).sp

    val context = LocalContext.current
    val inputView = remember(context) { NvimInputView(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                inputView.giveUpFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputView.giveUpFocus()
        }
    }

    var showCreateSession by remember { mutableStateOf(false) }
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

    fun selectTool(tool: WorkspaceTool) {
        val index = session.tools.indexOfFirst { it.id == tool.id }
        if (index < 0) return

        extraKeysState.clearModifiers()
        selectedIndex = index
        session.activateTool(tool.id)
        scope.launch {
            pagerState.scrollToPage(index)
        }
    }

    fun closeTool(tool: WorkspaceTool) {
        val closedIndex = session.tools.indexOfFirst { it.id == tool.id }
        if (closedIndex < 0) return

        val currentIndex = selectedIndex
        extraKeysState.clearModifiers()

        scope.launch {
            withContext(Dispatchers.IO) {
                session.closeTool(tool.id)
            }

            if (session.tools.isEmpty()) {
                selectedIndex = 0
            } else {
                val newIndex = when {
                    closedIndex < currentIndex -> currentIndex - 1
                    closedIndex > currentIndex -> currentIndex
                    else -> closedIndex
                }.coerceIn(0, session.tools.lastIndex)

                selectedIndex = newIndex
                session.activateTool(session.tools[newIndex].id)
                pagerState.scrollToPage(newIndex)
            }
        }
    }

    fun createSession(type: SessionType, inputs: Map<String, String>) {
        showCreateSession = false
        extraKeysState.clearModifiers()

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when (type) {
                        SessionType.NvimEmbedded -> session.startNvim()

                        SessionType.NvimUnixSocket ->
                            session.startNvimConnectToSock(
                                inputs["socketpath"].orEmpty(),
                            )
                    }
                }

                val lastIndex = session.tools.lastIndex
                if (lastIndex >= 0) {
                    selectedIndex = lastIndex
                    pagerState.scrollToPage(lastIndex)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message ?: e}"
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            WorkspaceControlBar(
                tools = session.tools,
                currentTool = currentTool,
                onSelectTool = ::selectTool,
                onCloseTool = ::closeTool,
                onAddTool = {
                    extraKeysState.clearModifiers()
                    showCreateSession = true
                },
                onKeyAction = { action ->
                    val tool = currentTool as? NvimTool
                    if (tool != null) {
                        when (action) {
                            is ExtraKeyAction.SendKeys ->
                                tool.nvimSession.sendInput(action.keys)

                            is ExtraKeyAction.Modifier -> Unit
                        }
                    }
                },
                onBack = onBack,
                onOpenKeyboard = { inputView.focusAndShowKeyboard() },
                extraKeysState = extraKeysState,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (session.tools.isEmpty()) {
                EmptyWorkspace(
                    onAddSession = { showCreateSession = true },
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false,
                ) { page ->
                    when (val tool = session.tools.getOrNull(page)) {
                        is NvimTool -> {
                            val failure by tool.nvimSession.failure
                                .collectAsStateWithLifecycle()

                            if (failure != null) {
                                LaunchedEffect(tool.id, failure) {
                                    errorMessage = failure?.message
                                        ?: "Neovim session ended"
                                    closeTool(tool)
                                }
                            } else {
                                val frame by tool.nvimSession.state
                                    .collectAsStateWithLifecycle()
                                NvimEditor(
                                    frame = frame,
                                    onResize = { width, height ->
                                        tool.nvimSession.resize(width, height)
                                    },
                                    onMouse = { event ->
                                        tool.nvimSession.sendMouse(event)
                                    },
                                    takeModifiers = {
                                        extraKeysState.takeModifierString()
                                    },
                                    onOpenUrlError = { message ->
                                        errorMessage = message
                                    },
                                    fontFamily = editorFontFamily,
                                    fontSize = editorFontSizeSp,
                                    lineHeight = editorLineHeightSp,
                                )
                            }
                        }

                        null -> Unit
                    }
                }
            }

            NvimInputCapture(
                view = inputView,
                onInput = { input ->
                    val tool = currentTool as? NvimTool
                    tool?.nvimSession?.sendInput(
                        extraKeysState.applyToInput(input),
                    )
                },
            )
        }
    }

    if (showCreateSession) {
        CreateSessionDialog(
            onDismiss = { showCreateSession = false },
            onSubmit = ::createSession,
        )
    }
}

@Composable
private fun EmptyWorkspace(onAddSession: () -> Unit) {
    EmptyState(
        title = "No session open",
        message = "Start a Neovim session to begin editing.",
        titleStyle = MaterialTheme.typography.titleMedium,
        titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        action = {
            Button(onClick = onAddSession) {
                Text("New session")
            }
        },
    )
}
