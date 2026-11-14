package com.ngtv.neodroid.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.ui.theme.Spacing

@Composable
fun CompactListItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    onClick: () -> Unit = {},
    selected: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    menuActions: List<ActionMenuItem> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (selected) colors.primaryContainer else Color.Transparent
    val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    val accentColor = colors.primary

    var menuExpanded by remember { mutableStateOf(false) }
    var pressPosition by remember { mutableStateOf(Offset.Zero) }
    var rowHeight by remember { mutableStateOf(0) }
    val hasMenu = menuActions.isNotEmpty()

    val density = LocalDensity.current
    val menuOffset = with(density) {
        DpOffset(
            x = pressPosition.x.toDp(),
            y = (pressPosition.y - rowHeight.toFloat()).toDp(),
        )
    }

    Box {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(containerColor)
                    .drawBehind {
                        if (selected) {
                            drawRect(
                                color = accentColor,
                                size = Size(3.dp.toPx(), size.height),
                            )
                        }
                    }
                    .onSizeChanged { rowHeight = it.height }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed && !change.previousPressed) {
                                    pressPosition = change.position
                                }
                            }
                        }
                    }
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (hasMenu) {
                            { menuExpanded = true }
                        } else {
                            null
                        },
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingContent != null) {
                    Box(modifier = Modifier.padding(end = Spacing.md)) {
                        leadingContent()
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    headlineContent()
                    supportingContent()
                }

                if (trailingContent != null) {
                    Box(modifier = Modifier.padding(start = Spacing.md)) {
                        trailingContent()
                    }
                }
            }
        }

        ActionMenu(
            expanded = menuExpanded && hasMenu,
            onDismissRequest = { menuExpanded = false },
            items = menuActions,
            offset = menuOffset,
        )
    }
}
