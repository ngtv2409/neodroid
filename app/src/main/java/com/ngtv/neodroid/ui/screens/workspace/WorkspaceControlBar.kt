package com.ngtv.neodroid.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.R
import com.ngtv.neodroid.nvim.input.DefaultExtraKeys
import com.ngtv.neodroid.nvim.input.ExtraKeyAction
import com.ngtv.neodroid.nvim.input.ExtraKeysBar
import com.ngtv.neodroid.nvim.input.ExtraKeysState
import com.ngtv.neodroid.nvim.input.rememberExtraKeysState
import com.ngtv.neodroid.sshUtils.NvimTool
import com.ngtv.neodroid.sshUtils.WorkspaceTool
import com.ngtv.neodroid.ui.components.RoundToolButton
import com.ngtv.neodroid.ui.theme.Spacing

/**
 * Bottom control bar with extra keys and functionalities
 */
@Composable
internal fun WorkspaceControlBar(
    tools: List<WorkspaceTool>,
    currentTool: WorkspaceTool?,
    onSelectTool: (WorkspaceTool) -> Unit,
    onAddTool: () -> Unit,
    onKeyAction: (ExtraKeyAction) -> Unit,
    onOpenKeyboard: () -> Unit,
    onOpenSideBar: () -> Unit,
    selectionMode: Boolean,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    extraKeysState: ExtraKeysState = rememberExtraKeysState(),
) {
    ExtraKeysBar(
        keys = DefaultExtraKeys.keys,
        onAction = onKeyAction,
        modifier = modifier,
        state = extraKeysState,
        trailing = {
            VerticalDivider(
                modifier = Modifier.height(20.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(modifier = Modifier.width(Spacing.xxs))
            RoundToolButton(
                icon = painterResource(R.drawable.ic_text_select_start),
                contentDescription = if (selectionMode) {
                    "Stop selecting text"
                } else {
                    "Select text"
                },
                selected = selectionMode,
                onClick = onToggleSelection,
            )
            Spacer(modifier = Modifier.width(Spacing.xxs))
            RoundToolButton(
                icon = painterResource(R.drawable.ic_keyboard),
                contentDescription = "Show keyboard",
                onClick = onOpenKeyboard,
            )
            Spacer(modifier = Modifier.width(Spacing.xxs))
            SessionSwitcher(
                tools = tools,
                currentTool = currentTool,
                onSelect = onSelectTool,
                onAdd = onAddTool,
            )
            Spacer(modifier = Modifier.width(Spacing.xxs))
            RoundToolButton(
                icon = painterResource(R.drawable.ic_menu),
                contentDescription = "Workspace menu",
                onClick = onOpenSideBar,
            )
        },
    )
}

internal data class ToolLabel(val title: String, val cwd: String?)

@Composable
internal fun toolLabel(tool: WorkspaceTool?): ToolLabel {
    if (tool !is NvimTool) {
        return ToolLabel(tool?.title ?: "Sessions", cwd = null)
    }

    val frame by tool.nvimSession.state.collectAsStateWithLifecycle()
    return ToolLabel(
        title = frame.title?.takeIf { it.isNotBlank() } ?: tool.title,
        cwd = frame.cwd?.takeIf { it.isNotBlank() },
    )
}

@Composable
private fun SessionSwitcher(
    tools: List<WorkspaceTool>,
    currentTool: WorkspaceTool?,
    onSelect: (WorkspaceTool) -> Unit,
    onAdd: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = toolLabel(currentTool)

    Box {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(
                    enabled = tools.isNotEmpty(),
                    role = Role.Button,
                ) { expanded = true }
                .semantics { contentDescription = "Select session ${currentLabel.title}" }
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentLabel.title,
                modifier = Modifier.widthIn(max = 92.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = Spacing.xxs)
                    .size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (tools.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No sessions") },
                    enabled = false,
                    onClick = {},
                )
            } else {
                tools.forEach { tool ->
                    key(tool.id) {
                        val label = toolLabel(tool)
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = label.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    label.cwd?.let { cwd ->
                                        Text(
                                            text = cwd,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            },
                            leadingIcon = {
                                if (tool.id == currentTool?.id) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelect(tool)
                            },
                        )
                    }
                }
            }
            DropdownMenuItem(
                text = { Text("New session") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onAdd()
                },
            )
        }
    }
}
