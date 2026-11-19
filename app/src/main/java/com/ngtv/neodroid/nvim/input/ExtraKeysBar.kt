package com.ngtv.neodroid.nvim.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.ui.theme.Spacing

// the extra key bar
@Composable
fun ExtraKeysBar(
    keys: List<ExtraKey>,
    onAction: (ExtraKeyAction) -> Unit,
    modifier: Modifier = Modifier,
    state: ExtraKeysState = rememberExtraKeysState(),
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EXTRA_KEYS_BAR_HEIGHT)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                keys.forEach { key ->
                    val modifier = (key.action as? ExtraKeyAction.Modifier)?.modifier
                    ExtraKeyButton(
                        key = key,
                        active = modifier != null && modifier in state.activeModifiers,
                        onClick = {
                            if (key.action is ExtraKeyAction.Modifier) {
                                state.toggle(key.action.modifier)
                            } else {
                                onAction(state.consume(key.action))
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.xs))
            trailing()
        }
    }
}

@Composable
private fun ExtraKeyButton(key: ExtraKey, active: Boolean, onClick: () -> Unit) {
    val background = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .height(EXTRA_KEY_HEIGHT)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                if (key.action is ExtraKeyAction.Modifier) {
                    this.selected = active
                }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

private val EXTRA_KEYS_BAR_HEIGHT = 40.dp
private val EXTRA_KEY_HEIGHT = 32.dp
