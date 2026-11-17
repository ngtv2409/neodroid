package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.nvim.input.DefaultExtraKeys
import com.ngtv.neodroid.nvim.input.ExtraKeyAction
import com.ngtv.neodroid.nvim.input.ExtraKeysBar
import com.ngtv.neodroid.nvim.input.ExtraKeysState
import com.ngtv.neodroid.nvim.input.rememberExtraKeysState
import com.ngtv.neodroid.sshUtils.WorkspaceTool
import com.ngtv.neodroid.ui.theme.Spacing

/**
 * Bottom control bar with extra keys and functionalities
 */
@Composable
internal fun WorkspaceControlBar(
    tools: List<WorkspaceTool>,
    currentTool: WorkspaceTool?,
    onSelectTool: (WorkspaceTool) -> Unit,
    onCloseTool: (WorkspaceTool) -> Unit,
    onAddTool: () -> Unit,
    onKeyAction: (ExtraKeyAction) -> Unit,
    onBack: () -> Unit,
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
            // back button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "<",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.xxs))
            SessionSwitcher(
                tools = tools,
                currentTool = currentTool,
                onSelect = onSelectTool,
                onClose = onCloseTool,
            )
            Spacer(modifier = Modifier.width(Spacing.xxs))
            AddSessionButton(onClick = onAddTool)
        },
    )
}

@Composable
private fun SessionSwitcher(
    tools: List<WorkspaceTool>,
    currentTool: WorkspaceTool?,
    onSelect: (WorkspaceTool) -> Unit,
    onClose: (WorkspaceTool) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = tools.isNotEmpty()) { expanded = true }
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentTool?.title ?: "Sessions",
                modifier = Modifier.widthIn(max = 92.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = " ▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tool.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Text(
                                text = if (tool.id == currentTool?.id) "✓" else " ",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.size(32.dp),
                                onClick = {
                                    expanded = false
                                    onClose(tool)
                                },
                            ) {
                                Text("×")
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
    }
}

@Composable
private fun AddSessionButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
