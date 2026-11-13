package com.ngtv.neodroid.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
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
