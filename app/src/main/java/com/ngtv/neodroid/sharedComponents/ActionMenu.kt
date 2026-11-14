package com.ngtv.neodroid.sharedComponents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.ui.theme.Spacing

@Immutable
data class ActionMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<ActionMenuItem>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 200.dp, max = 280.dp),
        offset = offset,
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            items.forEach { item ->
                ActionMenuRow(
                    item = item,
                    onDismissRequest = onDismissRequest,
                )
            }
        }
    }
}

@Composable
private fun ActionMenuRow(item: ActionMenuItem, onDismissRequest: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val contentColor = when {
        !item.enabled -> colors.onSurface.copy(alpha = 0.38f)
        item.destructive -> colors.error
        else -> colors.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(enabled = item.enabled) {
                onDismissRequest()
                item.onClick()
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
    }
}
