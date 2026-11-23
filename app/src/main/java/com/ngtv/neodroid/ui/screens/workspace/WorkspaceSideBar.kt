package com.ngtv.neodroid.ui.screens.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.R
import com.ngtv.neodroid.sshUtils.WorkspaceTool
import com.ngtv.neodroid.ui.theme.Spacing

@Composable
internal fun WorkspaceSideBar(
    tools: List<WorkspaceTool>,
    currentTool: WorkspaceTool?,
    onSelectTool: (WorkspaceTool) -> Unit,
    onCloseTool: (WorkspaceTool) -> Unit,
    onAddTool: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Spacing.sm),
    ) {
        Text(
            text = "Workspace",
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        NavigationDrawerItem(
            label = { Text("Back") },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                )
            },
            selected = false,
            onClick = onBack,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
        NavigationDrawerItem(
            label = { Text("New session") },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                )
            },
            selected = false,
            onClick = onAddTool,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = "Sessions",
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (tools.isEmpty()) {
            Text(
                text = "No sessions open",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            tools.forEach { tool ->
                key(tool.id) {
                    SideBarSessionRow(
                        tool = tool,
                        current = tool.id == currentTool?.id,
                        onSelect = { onSelectTool(tool) },
                        onClose = { onCloseTool(tool) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SideBarSessionRow(
    tool: WorkspaceTool,
    current: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val label = toolLabel(tool)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val icon = if (current) R.drawable.ic_check else R.drawable.ic_terminal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onSelect)
            .padding(start = Spacing.sm, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.title,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
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
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Close session ${label.title}",
            )
        }
    }
}
