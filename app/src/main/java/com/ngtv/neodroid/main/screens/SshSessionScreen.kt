package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.nvim.render.NvimEditor
import com.ngtv.neodroid.sharedComponents.ValidatedTextField
import com.ngtv.neodroid.sshUtils.NvimTool
import com.ngtv.neodroid.sshUtils.WorkspaceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                                NvimEditor(
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
