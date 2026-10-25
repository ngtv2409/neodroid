package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.nvim.NvimSession
import com.ngtv.neodroid.nvim.NvimState
import com.ngtv.neodroid.sharedComponents.PasswordInput
import com.ngtv.neodroid.sharedComponents.ValidatedTextField
import com.ngtv.neodroid.sshUtils.NvimTool
import com.ngtv.neodroid.sshUtils.SshSessionManager
import com.ngtv.neodroid.sshUtils.WorkspaceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException
import kotlin.collections.setValue

@Composable
fun SshSessionScreen(session: WorkspaceSession) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(-1) }
    val pagerState = rememberPagerState { session.tools.size }
    val scope = rememberCoroutineScope()
    var currentId by remember { mutableStateOf("") }
    var selectedToolAdd by remember { mutableStateOf<ToolType?>(null) }

    Box {
        Column(
            modifier = Modifier.statusBarsPadding(),
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = if (selectedTab >= 0) {
                    selectedTab
                } else {
                    0
                },
            ) {
                session.tools.forEachIndexed { index, tool ->
                    Tab(
                        selected = selectedTab == index,
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
                            ) {
                                Text(tool.title)
                                IconButton(onClick = {
                                    scope.launch {
                                        val closedIndex = session.tools.indexOfFirst {
                                            it.id == tool.id
                                        }
                                        val currentIndex = session.tools.indexOfFirst {
                                            it.id ==
                                                currentId
                                        }

                                        withContext(Dispatchers.IO) {
                                            session.closeTool(tool.id)
                                        }

                                        selectedTab = if (tool.id == currentId) {
                                            when {
                                                session.tools.isEmpty() -> -1

                                                // is the last tool, go backward
                                                closedIndex == session.tools.size -> closedIndex - 1

                                                // is not the last tool, keep the index
                                                // this will "advance" the tab forward
                                                closedIndex > 0 -> closedIndex

                                                else -> 0
                                            }
                                        } else if (closedIndex < currentIndex) {
                                            currentIndex - 1
                                        } else {
                                            currentIndex
                                        }
                                    }
                                }) {
                                    Text("×")
                                }
                            }
                        },
                    )
                }
                AddToolDropdown(onToolSelected = { tool ->
                    selectedToolAdd = tool
                })
            }

            if (session.tools.isEmpty()) {
                Text("No tools available")
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (val tool = session.tools[page]) {
                        is NvimTool -> {
                            val state by tool.nvimSession.state.collectAsStateWithLifecycle()
                            NvimStateRender(state)
                        }
                    }
                }
            }
        }
        //
        if (selectedToolAdd != null) {
            CreateToolDialog(
                tool = selectedToolAdd as ToolType,
                onDismiss = { selectedToolAdd = null },
                onSubmit = { inputMap ->
                    when (selectedToolAdd as ToolType) {
                        ToolType.NvimEmbedded -> {
                            selectedToolAdd = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    session.startNvim()
                                    selectedTab = session.tools.size - 1
                                }
                            }
                        }

                        ToolType.NvimUnixSocket -> {
                            selectedToolAdd = null
                        }

                        else -> {}
                    }
                },
            )
        }
    }
}

@Composable
fun NvimStateRender(state: NvimState) {
    Text("$state")
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
private enum class ToolType(val label: String, val fields: List<ToolField> = emptyList()) {
    NvimEmbedded("Launch Neovim"),
    NvimUnixSocket(
        "Connect to Nvim over UNIX socket",
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
