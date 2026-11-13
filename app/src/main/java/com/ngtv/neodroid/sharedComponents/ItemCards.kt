package com.ngtv.neodroid.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (selected) colors.primaryContainer else Color.Transparent
    val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    val accentColor = colors.primary

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
                .clickable {
                    onClick()
                }
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
}
